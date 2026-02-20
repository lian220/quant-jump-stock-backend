"""
Analysis Notifier Adapter for Slack

Bot Token 우선 (스레드 답글 지원), Webhook fallback.
AnalysisNotifierPort 인터페이스 구현.
"""

import asyncio
import logging
import requests
from typing import Optional
from datetime import datetime
from pytz import timezone

from application.analysis.ports import AnalysisNotifierPort
from config.settings import Settings
from adapter.output.slack.bot_client import SlackBotClient

KST = timezone('Asia/Seoul')
logger = logging.getLogger(__name__)


class SlackAnalysisNotifierAdapter(AnalysisNotifierPort):
    """
    Slack 분석 알림 어댑터

    Bot Token 사용 시 스레드 답글 지원.
    Bot Token 없으면 Webhook fallback.
    """

    def __init__(self, settings: Settings):
        self.settings = settings
        self._bot_client = SlackBotClient(bot_token=settings.slack.bot_token)

    def _get_scheduler_channel(self) -> str:
        return self.settings.slack.channel_scheduler

    def _get_analysis_channel(self) -> str:
        return self.settings.slack.channel_analysis

    def _get_error_channel(self) -> str:
        return self.settings.slack.channel_error

    def _get_scheduler_webhook(self) -> str:
        return self.settings.slack.webhook_url_scheduler or self.settings.slack.webhook_url

    def _get_analysis_webhook(self) -> str:
        return self.settings.slack.webhook_url_analysis or self.settings.slack.webhook_url

    def _get_error_webhook(self) -> str:
        return self.settings.slack.webhook_url_error or self.settings.slack.webhook_url

    def _post_message(
        self,
        channel: str,
        webhook_url: str,
        text: str,
        attachments: list | None = None,
        thread_ts: Optional[str] = None,
    ) -> Optional[str]:
        """Bot Token 우선, Webhook fallback. Returns ts."""
        if not self.settings.SLACK_ENABLED:
            logger.info("Slack 비활성화 상태 (SLACK_ENABLED=false)")
            return None

        if self._bot_client.is_available and channel:
            return self._bot_client.post_message(
                channel=channel,
                text=text,
                attachments=attachments,
                thread_ts=thread_ts,
            )

        # Webhook fallback
        self._post_to_webhook(webhook_url, text, attachments)
        return None

    def _post_to_webhook(self, url: str, text: str, attachments: list | None = None) -> None:
        if not url:
            return
        if not self.settings.SLACK_ENABLED:
            return
        try:
            payload = {"text": text}
            if attachments:
                payload["attachments"] = attachments
            response = requests.post(url, json=payload, timeout=10)
            response.raise_for_status()
        except Exception as e:
            logger.exception(f"Slack Webhook 알림 발송 실패: {e}")

    async def notify_analysis_start(
        self,
        analysis_type: str,
        thread_ts: Optional[str]
    ) -> Optional[str]:
        """
        분석 시작 알림.

        Returns:
            새 메시지의 ts (Bot Token 사용 시). 이후 답글에 사용.
        """
        if analysis_type == "technical":
            emoji = "📊"
            type_name = "기술적 분석"
            desc = "SMA, RSI, MACD 기술적 지표 분석 중"
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

        ts = await asyncio.to_thread(
            self._post_message,
            self._get_scheduler_channel(),
            self._get_scheduler_webhook(),
            text,
            attachments,
            thread_ts,
        )
        return ts or thread_ts

    async def notify_analysis_complete(
        self,
        analysis_type: str,
        total_analyzed: int,
        recommended_count: int,
        thread_ts: Optional[str]
    ) -> None:
        """분석 완료 알림 (thread reply)"""
        type_name = "기술적 분석" if analysis_type == "technical" else "뉴스 감정 분석"
        text = f"✅ {type_name} 완료"
        attachments = [
            {
                "color": "28a745",
                "title": f"{type_name} 완료",
                "fields": [
                    {"title": "분석 종목", "value": f"{total_analyzed}개", "short": True},
                    {"title": "추천 종목", "value": f"{recommended_count}개", "short": True},
                    {"title": "완료 시각", "value": datetime.now(KST).strftime("%Y-%m-%d %H:%M:%S"), "short": True},
                ],
                "footer": "종합 추천 결과는 analysis 채널 확인 | Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        await asyncio.to_thread(
            self._post_message,
            self._get_scheduler_channel(),
            self._get_scheduler_webhook(),
            text,
            attachments,
            thread_ts,
        )

    async def notify_analysis_error(
        self,
        analysis_type: str,
        error: str,
        thread_ts: Optional[str]
    ) -> None:
        """분석 오류 알림 (thread reply)"""
        type_name = "기술적 분석" if analysis_type == "technical" else "뉴스 감정 분석"
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

        await asyncio.to_thread(
            self._post_message,
            self._get_error_channel(),
            self._get_error_webhook(),
            text,
            attachments,
            thread_ts,
        )
