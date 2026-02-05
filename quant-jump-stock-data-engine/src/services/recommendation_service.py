"""
RecommendationService - 통합 분석 서비스
기술적 분석 + 감정 분석 + 통합 점수 계산
"""
import logging
from typing import Dict, Any, List
from datetime import datetime

from src.services.technical_analysis import TechnicalAnalysisService
from src.services.sentiment_analysis import SentimentAnalysisService
from src.services.slack_notifier import SlackNotifier

logger = logging.getLogger(__name__)


class RecommendationService:
    """
    추천 서비스 통합 레이어
    """

    def __init__(self):
        self.technical_service = TechnicalAnalysisService()
        self.sentiment_service = SentimentAnalysisService()

    def run_technical_analysis(self, request_id: str, thread_ts: str = None, target_date: str = None) -> Dict[str, Any]:
        """
        기술적 분석 전체 플로우

        Args:
            request_id: 요청 ID
            thread_ts: Slack 스레드 타임스탬프
            target_date: 분석 기준 날짜 (YYYY-MM-DD)

        Returns:
            분석 결과
        """
        try:
            logger.info(f"[{request_id}] 기술적 분석 시작 (target_date={target_date})")

            # 시작 알림
            if thread_ts:
                SlackNotifier.send_thread_message(
                    "🔄 기술적 지표 분석 시작...\nSMA, RSI, MACD 계산 중",
                    thread_ts
                )

            # 분석 실행
            results = self.technical_service.analyze_stocks(target_date=target_date)

            # 추천 종목 필터링
            recommended = [r for r in results if r.get("is_recommended", False)]

            # 완료 알림
            if thread_ts:
                SlackNotifier.send_thread_message(
                    f"✅ 기술적 분석 완료\n"
                    f"• 분석 종목: {len(results)}개\n"
                    f"• 추천 종목: {len(recommended)}개\n"
                    f"• 시각: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
                    thread_ts
                )

            logger.info(f"[{request_id}] 기술적 분석 완료: 추천 {len(recommended)}개")

            return {
                "status": "success",
                "total_analyzed": len(results),
                "recommended_count": len(recommended),
                "results": results
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

    def run_sentiment_analysis(self, request_id: str, thread_ts: str = None) -> Dict[str, Any]:
        """
        뉴스 감정 분석 전체 플로우

        Args:
            request_id: 요청 ID
            thread_ts: Slack 스레드 타임스탬프

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
