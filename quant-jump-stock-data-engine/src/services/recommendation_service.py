"""
RecommendationService - 통합 분석 서비스
기술적 분석 + 감정 분석 + 통합 점수 계산
"""
import logging
from typing import Dict, Any, List
from datetime import datetime

from services.technical_analysis import TechnicalAnalysisService
from services.sentiment_analysis import SentimentAnalysisService
from services.slack_notifier import SlackNotifier
from services.buy_criteria import BuyCriteria

logger = logging.getLogger(__name__)


class RecommendationService:
    """
    추천 서비스 통합 레이어
    """

    def __init__(self):
        self.technical_service = TechnicalAnalysisService()
        self.sentiment_service = SentimentAnalysisService()
        self.buy_criteria = BuyCriteria()

    def run_technical_analysis(self, request_id: str, thread_ts: str = None, start_date: str = None, end_date: str = None) -> Dict[str, Any]:
        """
        기술적 분석 전체 플로우

        Args:
            request_id: 요청 ID
            thread_ts: Slack 스레드 타임스탬프
            start_date: 분석 시작 날짜 (YYYY-MM-DD)
            end_date: 분석 종료 날짜 (YYYY-MM-DD)

        Returns:
            분석 결과
        """
        try:
            # 하위 호환: target_date로 end_date 사용
            target_date = end_date or start_date
            logger.info(f"[{request_id}] 기술적 분석 시작 (start_date={start_date}, end_date={end_date})")

            # 시작 알림
            if thread_ts:
                SlackNotifier.send_thread_message(
                    "🔄 기술적 지표 분석 시작...\nSMA, RSI, MACD 계산 중",
                    thread_ts
                )

            # 분석 실행 (dict 반환: total_analyzed, all_results, recommendations)
            analysis_result = self.technical_service.analyze_stocks(target_date=target_date)
            total_analyzed = analysis_result.get("total_analyzed", 0)
            all_results = analysis_result.get("all_results", [])

            # 완료 알림 (스레드)
            # Note: Composite Score 필터링과 최종 추천은 main.py의 ComprehensiveReportService에서 처리
            if thread_ts:
                SlackNotifier.send_thread_message(
                    f"✅ 기술적 분석 완료\n"
                    f"• 분석 종목: {total_analyzed}개\n"
                    f"• 시각: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
                    thread_ts
                )

            logger.info(f"[{request_id}] 기술적 분석 완료: {total_analyzed}개 분석")

            return {
                "status": "success",
                "total_analyzed": total_analyzed,
                "recommended_count": 0,  # ComprehensiveReportService에서 업데이트됨
                "results": all_results  # 전체 분석 결과 반환 (필터링 전)
            }

        except Exception as e:
            logger.error(f"[{request_id}] 기술적 분석 실패: {e}")

            if thread_ts:
                SlackNotifier.send_thread_message(
                    f"❌ 기술적 분석 실패\n오류: {str(e)}",
                    thread_ts
                )

            return {
                "status": "failed",
                "error": str(e)
            }

    def run_sentiment_analysis(self, request_id: str, thread_ts: str = None, start_date: str = None, end_date: str = None) -> Dict[str, Any]:
        """
        뉴스 감정 분석 전체 플로우

        Args:
            request_id: 요청 ID
            thread_ts: Slack 스레드 타임스탬프
            start_date: 분석 시작 날짜 (YYYY-MM-DD)
            end_date: 분석 종료 날짜 (YYYY-MM-DD)

        Returns:
            분석 결과
        """
        try:
            logger.info(f"[{request_id}] 뉴스 감정 분석 시작")

            # 시작 알림
            if thread_ts:
                SlackNotifier.send_thread_message(
                    "🔄 뉴스 감정 분석 시작...\nAlpha Vantage NEWS_SENTIMENT API 호출 중",
                    thread_ts
                )

            # 분석 실행
            results = self.sentiment_service.fetch_and_store_sentiment()

            # 평균 감정 점수 계산
            avg_score = sum(r.get("average_sentiment_score", 0) for r in results) / len(results) if results else 0

            # 완료 알림
            if thread_ts:
                SlackNotifier.send_thread_message(
                    f"✅ 뉴스 감정 분석 완료\n"
                    f"• 분석 종목: {len(results)}개\n"
                    f"• 평균 감정 점수: {avg_score:.2f}\n"
                    f"• 시각: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
                    thread_ts
                )

            logger.info(f"[{request_id}] 뉴스 감정 분석 완료: {len(results)}개 종목")

            return {
                "status": "success",
                "total_analyzed": len(results),
                "average_score": avg_score,
                "results": results
            }

        except Exception as e:
            logger.error(f"[{request_id}] 뉴스 감정 분석 실패: {e}")

            if thread_ts:
                SlackNotifier.send_thread_message(
                    f"❌ 뉴스 감정 분석 실패\n오류: {str(e)}",
                    thread_ts
                )

            return {
                "status": "failed",
                "error": str(e)
            }
