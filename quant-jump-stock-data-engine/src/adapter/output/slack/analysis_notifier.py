"""
Analysis Notifier Adapter for Slack

Slack 분석 알림 어댑터 (Webhook 기반, Bot Token 불필요).
AnalysisNotifierPort 인터페이스 구현.
"""

import asyncio
import logging
import requests
from typing import Optional, List
from datetime import datetime
from pytz import timezone

from application.analysis.ports import AnalysisNotifierPort
from config.settings import Settings

KST = timezone('Asia/Seoul')
logger = logging.getLogger(__name__)


class SlackAnalysisNotifierAdapter(AnalysisNotifierPort):
    """
    Slack 분석 알림 어댑터 (Webhook 전용)

    기술적 분석 및 감정 분석의 시작/완료/오류 알림을
    분석 전용 Webhook으로 전송합니다.
    """

    def __init__(self, settings: Settings):
        self.settings = settings

    def _get_analysis_webhook(self) -> str:
        return self.settings.slack.webhook_url_analysis or self.settings.slack.webhook_url

    def _get_error_webhook(self) -> str:
        return self.settings.slack.webhook_url_error or self.settings.slack.webhook_url

    def _post_to_webhook(self, url: str, text: str, attachments: list = None) -> None:
        """지정 Webhook URL로 메시지 POST"""
        if not url:
            logger.debug("Webhook URL이 비어있어 메시지를 보내지 않습니다")
            return

        if not self.settings.SLACK_ENABLED:
            logger.info("Slack 비활성화 상태 (SLACK_ENABLED=false)")
            return

        try:
            payload = {"text": text}
            if attachments:
                payload["attachments"] = attachments

            response = requests.post(url, json=payload, timeout=10)
            response.raise_for_status()
            logger.debug("Slack Webhook 알림 발송 완료")
        except Exception as e:
            logger.error(f"Slack Webhook 알림 발송 실패: {e}")

    async def notify_analysis_start(
        self,
        analysis_type: str,
        thread_ts: Optional[str]
    ) -> None:
        """분석 시작 알림 → 분석 채널"""
        if analysis_type == "technical":
            emoji = "🎯"
            type_name = "Quantiq 종합 분석"
            desc = "기술적 지표 + AI 예측 + 감정 분석 종합 처리 중"
        else:
            emoji = "💬"
            type_name = "뉴스 감정 분석"
            desc = "Alpha Vantage NEWS_SENTIMENT API 호출 중"

        text = f"{emoji} {type_name} 시작..."

        attachments = [
            {
                "color": "36a64f",
                "title": f"{type_name} 진행 중",
                "text": desc,
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        await asyncio.to_thread(self._post_to_webhook, self._get_analysis_webhook(), text, attachments)

    async def notify_analysis_complete(
        self,
        analysis_type: str,
        total_analyzed: int,
        recommended_count: int,
        thread_ts: Optional[str]
    ) -> None:
        """분석 완료 알림 → 분석 채널"""
        if analysis_type == "technical":
            type_name = "Quantiq 종합 분석"
        else:
            type_name = "뉴스 감정 분석"

        text = f"✅ {type_name} 완료"

        attachments = [
            {
                "color": "28a745",
                "title": f"{type_name} 완료",
                "fields": [
                    {"title": "분석 종목", "value": f"{total_analyzed}개", "short": True},
                    {"title": "완료 시각", "value": datetime.now(KST).strftime("%Y-%m-%d %H:%M:%S"), "short": True},
                ],
                "footer": "종합 추천 결과는 analysis 채널 확인 | Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        await asyncio.to_thread(self._post_to_webhook, self._get_analysis_webhook(), text, attachments)

    async def notify_analysis_error(
        self,
        analysis_type: str,
        error: str,
        thread_ts: Optional[str]
    ) -> None:
        """분석 오류 알림 → 에러 채널"""
        type_name = "Quantiq 종합 분석" if analysis_type == "technical" else "뉴스 감정 분석"

        text = f"❌ {type_name} 실패"

        attachments = [
            {
                "color": "dc3545",
                "title": f"{type_name} 오류 발생",
                "fields": [
                    {"title": "오류 내용", "value": error[:200], "short": False},
                    {"title": "발생 시각", "value": datetime.now(KST).strftime("%Y-%m-%d %H:%M:%S"), "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        await asyncio.to_thread(self._post_to_webhook, self._get_error_webhook(), text, attachments)
