"""
Slack Notifier Adapter

Bot Token 우선 (스레드 답글 지원), Webhook fallback.
NotificationPort 인터페이스 구현.
"""

import logging
import requests
from typing import Optional, Dict, Any
from datetime import datetime
from pytz import timezone

from application.ports.output_ports import NotificationPort, TradingSignal
from config.settings import Settings
from adapter.output.slack.bot_client import SlackBotClient

KST = timezone('Asia/Seoul')
logger = logging.getLogger(__name__)


class SlackNotifierAdapter(NotificationPort):
    """
    Slack 알림 어댑터

    Bot Token 사용 시 스레드 답글 지원.
    Bot Token 없으면 Webhook fallback.
    """

    def __init__(self, settings: Settings):
        self.settings = settings
        self._bot_client = SlackBotClient(bot_token=settings.slack.bot_token)

    def _post_message(
        self,
        channel: str,
        webhook_url: str,
        text: str,
        attachments: list | None = None,
        thread_ts: Optional[str] = None,
    ) -> Optional[str]:
        """Bot Token 우선, Webhook fallback."""
        if not self.settings.SLACK_ENABLED:
            return None

        if self._bot_client.is_available and channel:
            return self._bot_client.post_message(
                channel=channel,
                text=text,
                attachments=attachments,
                thread_ts=thread_ts,
            )

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
        except requests.exceptions.RequestException as e:
            logger.exception(f"Slack Webhook 알림 발송 실패: {e}")

    def _get_trading_channel(self) -> str:
        return self.settings.slack.channel_trading

    def _get_error_channel(self) -> str:
        return self.settings.slack.channel_error

    def _get_scheduler_channel(self) -> str:
        return self.settings.slack.channel_scheduler

    def _get_trading_webhook(self) -> str:
        return self.settings.slack.webhook_url_trading or self.settings.slack.webhook_url

    def _get_error_webhook(self) -> str:
        return self.settings.slack.webhook_url_error or self.settings.slack.webhook_url

    def _get_scheduler_webhook(self) -> str:
        return self.settings.slack.webhook_url_scheduler or self.settings.slack.webhook_url

    async def send_signal_alert(
        self,
        signal: TradingSignal,
        channel: Optional[str] = None
    ) -> None:
        color = "28a745" if signal.signal_type.value == "buy" else "dc3545"
        emoji = "📈" if signal.signal_type.value == "buy" else "📉"

        text = f"{emoji} 매매 신호 발생"
        attachments = [
            {
                "color": color,
                "title": f"{signal.symbol} - {signal.signal_type.value.upper()}",
                "fields": [
                    {"title": "전략", "value": signal.strategy_id, "short": True},
                    {"title": "규칙", "value": signal.rule_name, "short": True},
                    {"title": "가중치", "value": f"{signal.weight:.2f}", "short": True},
                    {"title": "시간", "value": signal.timestamp.strftime("%Y-%m-%d %H:%M:%S"), "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        self._post_message(
            channel=channel or self._get_trading_channel(),
            webhook_url=self._get_trading_webhook(),
            text=text,
            attachments=attachments,
        )

    async def send_error_alert(
        self,
        error_type: str,
        message: str,
        details: Optional[Dict[str, Any]] = None
    ) -> None:
        text = f"⚠️ {error_type}"
        fields = [
            {"title": "Error", "value": message, "short": False},
            {"title": "Timestamp", "value": datetime.now(KST).isoformat(), "short": True},
        ]
        if details:
            for key, value in list(details.items())[:3]:
                fields.append({"title": key, "value": str(value)[:100], "short": True})

        attachments = [
            {
                "color": "dc3545",
                "title": "오류 발생",
                "fields": fields,
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        self._post_message(
            channel=self._get_error_channel(),
            webhook_url=self._get_error_webhook(),
            text=text,
            attachments=attachments,
        )

    async def send_daily_summary(
        self,
        date: datetime,
        signals_count: int,
        strategies_executed: int,
        errors_count: int
    ) -> None:
        status_color = "28a745" if errors_count == 0 else "ffc107"
        text = "📊 일일 요약 리포트"
        attachments = [
            {
                "color": status_color,
                "title": f"{date.strftime('%Y-%m-%d')} 운영 현황",
                "fields": [
                    {"title": "실행된 전략", "value": str(strategies_executed), "short": True},
                    {"title": "생성된 신호", "value": str(signals_count), "short": True},
                    {"title": "오류 건수", "value": str(errors_count), "short": True},
                    {"title": "상태", "value": "정상" if errors_count == 0 else "점검 필요", "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        self._post_message(
            channel=self._get_scheduler_channel(),
            webhook_url=self._get_scheduler_webhook(),
            text=text,
            attachments=attachments,
        )
