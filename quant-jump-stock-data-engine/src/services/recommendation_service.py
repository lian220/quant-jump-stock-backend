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

    def run_combined_analysis(self, request_id: str, thread_ts: str = None, target_date: str = None) -> Dict[str, Any]:
        """
        통합 분석 (3단계)
        1. 기술적 분석
        2. 감정 분석
        3. 통합 점수 계산

        Args:
            request_id: 요청 ID
            thread_ts: Slack 스레드 타임스탬프
            target_date: 분석 기준 날짜 (YYYY-MM-DD)

        Returns:
            통합 분석 결과
        """
        try:
            logger.info(f"[{request_id}] 통합 분석 시작 (target_date={target_date})")

            # 1단계: 기술적 분석
            if thread_ts:
                SlackNotifier.send_thread_message(
                    "🔄 1단계: 기술적 지표 분석 중...",
                    thread_ts
                )

            tech_results = self.technical_service.analyze_stocks(target_date=target_date)
            tech_recommended = [r for r in tech_results if r.get("is_recommended", False)]

            logger.info(f"[{request_id}] 1단계 완료: 기술적 분석 {len(tech_recommended)}개 추천")

            # 2단계: 감정 분석
            if thread_ts:
                SlackNotifier.send_thread_message(
                    f"✅ 1단계 완료: {len(tech_recommended)}개 추천\n"
                    f"🔄 2단계: 뉴스 감정 분석 중...",
                    thread_ts
                )

            sentiment_results = self.sentiment_service.fetch_and_store_sentiment()
            avg_sentiment = sum(r.get("average_sentiment_score", 0) for r in sentiment_results) / len(sentiment_results) if sentiment_results else 0

            logger.info(f"[{request_id}] 2단계 완료: 감정 분석 {len(sentiment_results)}개 종목")

            # 3단계: 통합 점수 계산
            if thread_ts:
                SlackNotifier.send_thread_message(
                    f"✅ 2단계 완료: {len(sentiment_results)}개 분석\n"
                    f"🔄 3단계: 통합 점수 계산 중...",
                    thread_ts
                )

            final_recommendations = self._calculate_final_score(tech_results, sentiment_results)

            logger.info(f"[{request_id}] 3단계 완료: 최종 추천 {len(final_recommendations)}개")

            # 최종 완료 알림
            if thread_ts:
                top_3 = final_recommendations[:3]
                top_list = "\n".join([
                    f"  {i+1}. {r['ticker']} (점수: {r.get('combined_score', 0):.2f})"
                    for i, r in enumerate(top_3)
                ])

                SlackNotifier.send_thread_message(
                    f"✅ 통합 분석 완료!\n"
                    f"• 최종 추천: {len(final_recommendations)}개\n"
                    f"• Top 3:\n{top_list}\n"
                    f"• 시각: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
                    thread_ts
                )

            return {
                "status": "success",
                "technical_analyzed": len(tech_results),
                "technical_recommended": len(tech_recommended),
                "sentiment_analyzed": len(sentiment_results),
                "final_recommendations": len(final_recommendations),
                "recommendations": final_recommendations
            }

        except Exception as e:
            logger.error(f"[{request_id}] 통합 분석 실패: {e}")

            if thread_ts:
                SlackNotifier.send_thread_message(
                    f"❌ 통합 분석 실패\n오류: {str(e)}",
                    thread_ts
                )

            return {
                "status": "failed",
                "error": str(e)
            }

    def _calculate_final_score(self, tech_results: List[Dict], sentiment_results: List[Dict]) -> List[Dict]:
        """
        기술적 분석 + 감정 분석 통합 점수 계산

        Args:
            tech_results: 기술적 분석 결과
            sentiment_results: 감정 분석 결과

        Returns:
            통합 점수가 계산된 추천 목록 (점수 내림차순 정렬)
        """
        # 감정 점수를 ticker별로 매핑
        sentiment_map = {
            r["ticker"]: r.get("average_sentiment_score", 0)
            for r in sentiment_results
        }

        # 기술적 분석 결과에 감정 점수 추가
        combined = []
        for tech in tech_results:
            ticker = tech.get("ticker")
            if not ticker:
                continue

            # 기술적 점수 (0-100 → 0-1 정규화)
            technical_score = tech.get("composite_score", 0) / 100.0

            # 감정 점수 (-1 ~ 1 → 0-1 정규화)
            sentiment_score = sentiment_map.get(ticker, 0)
            sentiment_normalized = (sentiment_score + 1) / 2.0

            # 가중 평균 (sentiment 없으면 technical 100%, 있으면 기술적 70% + 감정 30%)
            if sentiment_results:
                combined_score = (technical_score * 0.7) + (sentiment_normalized * 0.3)
            else:
                combined_score = technical_score  # sentiment 비활성화 시 technical만 사용

            combined.append({
                "ticker": ticker,
                "technical_score": technical_score,
                "sentiment_score": sentiment_score,
                "combined_score": combined_score,
                "is_recommended": combined_score >= 0.6,  # 임계값 0.6
                **tech  # 기존 기술적 지표 데이터 포함
            })

        # 통합 점수로 내림차순 정렬
        combined.sort(key=lambda x: x["combined_score"], reverse=True)

        # 추천 종목만 필터링
        recommended = [r for r in combined if r["is_recommended"]]

        return recommended
