"""
Slack Notifier Adapter

Slack 알림을 전송하는 어댑터.
NotificationPort 인터페이스 구현.
"""

import logging
import requests
from typing import Optional, Dict, Any
from datetime import datetime
from pytz import timezone

from application.ports.output_ports import NotificationPort, TradingSignal
from config.settings import Settings

KST = timezone('Asia/Seoul')
logger = logging.getLogger(__name__)


class SlackNotifierAdapter(NotificationPort):
    """
    Slack 알림 어댑터

    Slack API 또는 Webhook을 통해 알림 전송.
    스레드 답글 지원.
    """

    SLACK_API_URL = "https://slack.com/api/chat.postMessage"

    def __init__(self, settings: Settings):
        self.settings = settings
        self._thread_cache: Dict[str, str] = {}

    def _can_send(self) -> bool:
        """Slack 설정 확인"""
        return bool(self.settings.SLACK_BOT_TOKEN or self.settings.SLACK_WEBHOOK_URL)

    def _post_message(
        self,
        text: str,
        attachments: list = None,
        thread_ts: Optional[str] = None
    ) -> Optional[str]:
        """메시지 전송"""
        if not self._can_send():
            logger.warning("Slack configuration not found")
            return None

        if self.settings.SLACK_BOT_TOKEN:
            return self._post_via_api(text, attachments, thread_ts)
        else:
            return self._post_via_webhook(text, attachments)

    def _post_via_api(
        self,
        text: str,
        attachments: list = None,
        thread_ts: Optional[str] = None
    ) -> Optional[str]:
        """Slack API 사용"""
        try:
            headers = {
                "Authorization": f"Bearer {self.settings.SLACK_BOT_TOKEN}",
                "Content-Type": "application/json"
            }

            payload = {
                "channel": self.settings.SLACK_CHANNEL,
                "text": text,
            }

            if attachments:
                payload["attachments"] = attachments
            if thread_ts:
                payload["thread_ts"] = thread_ts

            response = requests.post(
                self.SLACK_API_URL,
                headers=headers,
                json=payload,
                timeout=5
            )
            response.raise_for_status()

            data = response.json()
            if data.get("ok"):
                return data.get("ts")
            else:
                logger.error(f"Slack API error: {data.get('error')}")
                return None

        except Exception as e:
            logger.error(f"Slack message failed: {e}")
            return None

    def _post_via_webhook(self, text: str, attachments: list = None) -> Optional[str]:
        """Slack Webhook 사용"""
        try:
            payload = {"text": text}
            if attachments:
                payload["attachments"] = attachments

            response = requests.post(
                self.settings.SLACK_WEBHOOK_URL,
                json=payload,
                timeout=5
            )
            response.raise_for_status()
            return None

        except Exception as e:
            logger.error(f"Slack webhook failed: {e}")
            return None

    async def send_signal_alert(
        self,
        signal: TradingSignal,
        channel: Optional[str] = None
    ) -> None:
        """매매 신호 알림"""
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

        self._post_message(text, attachments)

    async def send_error_alert(
        self,
        error_type: str,
        message: str,
        details: Optional[Dict[str, Any]] = None
    ) -> None:
        """에러 알림"""
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

        self._post_message(text, attachments)

    async def send_daily_summary(
        self,
        date: datetime,
        signals_count: int,
        strategies_executed: int,
        errors_count: int
    ) -> None:
        """일일 요약"""
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

        self._post_message(text, attachments)
