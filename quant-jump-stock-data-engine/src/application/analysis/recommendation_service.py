"""
Recommendation Application Service

기술적 분석과 감정 분석을 통합하는 서비스.
"""

import logging
from typing import Dict, Any, Optional

from .technical_service import TechnicalAnalysisApplicationService
from .ports import AnalysisNotifierPort

logger = logging.getLogger(__name__)


class RecommendationApplicationService:
    """
    추천 통합 서비스

    기술적 분석과 감정 분석을 오케스트레이션합니다.
    """

    def __init__(
        self,
        technical_service: TechnicalAnalysisApplicationService,
        sentiment_service=None,  # 추후 구현
        notifier: Optional[AnalysisNotifierPort] = None
    ):
        self.technical_service = technical_service
        self.sentiment_service = sentiment_service
        self.notifier = notifier

    async def run_technical_analysis(
        self,
        request_id: str,
        thread_ts: Optional[str] = None,
        target_date: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        기술적 분석 실행

        Args:
            request_id: 요청 ID
            thread_ts: Slack 스레드 타임스탬프
            target_date: 분석 기준 날짜

        Returns:
            분석 결과
        """
        return await self.technical_service.analyze_stocks(
            request_id=request_id,
            thread_ts=thread_ts,
            target_date=target_date
        )

    async def run_sentiment_analysis(
        self,
        request_id: str,
        thread_ts: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        감정 분석 실행 (TODO: 구현 필요)

        Args:
            request_id: 요청 ID
            thread_ts: Slack 스레드 타임스탬프

        Returns:
            분석 결과
        """
        if self.sentiment_service is None:
            logger.warning("Sentiment service not configured")
            return {
                "status": "skipped",
                "message": "Sentiment service not configured"
            }

        # TODO: 감정 분석 서비스 연동
        return await self.sentiment_service.analyze(request_id, thread_ts)

    async def run_combined_analysis(
        self,
        request_id: str,
        thread_ts: Optional[str] = None,
        target_date: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        통합 분석 실행 (기술적 + 감정)

        Args:
            request_id: 요청 ID
            thread_ts: Slack 스레드 타임스탬프
            target_date: 분석 기준 날짜

        Returns:
            통합 분석 결과
        """
        logger.info(f"[{request_id}] 통합 분석 시작")

        # 기술적 분석
        technical_result = await self.run_technical_analysis(
            request_id, thread_ts, target_date
        )

        # 감정 분석
        sentiment_result = await self.run_sentiment_analysis(
            request_id, thread_ts
        )

        # 결과 통합 (추후 가중치 기반 점수 계산)
        return {
            "status": "success",
            "technical": technical_result,
            "sentiment": sentiment_result,
            "combined_score": None  # TODO: 통합 점수 계산
        }
