"""
Analysis Notifier Adapter for Slack

Slack 분석 알림 어댑터.
AnalysisNotifierPort 인터페이스 구현.
"""

import logging
import requests
from typing import Optional
from datetime import datetime
from pytz import timezone

from application.analysis.ports import AnalysisNotifierPort
from config.settings import Settings

KST = timezone('Asia/Seoul')
logger = logging.getLogger(__name__)


class SlackAnalysisNotifierAdapter(AnalysisNotifierPort):
    """
    Slack 분석 알림 어댑터

    기술적 분석 및 감정 분석의 시작/완료/오류 알림을 Slack으로 전송합니다.
    스레드 답글 지원.
    """

    SLACK_API_URL = "https://slack.com/api/chat.postMessage"

    def __init__(self, settings: Settings):
        self.settings = settings

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

    async def notify_analysis_start(
        self,
        analysis_type: str,
        thread_ts: Optional[str]
    ) -> None:
        """분석 시작 알림"""
        emoji_map = {
            "technical": "📊",
            "sentiment": "💬"
        }
        emoji = emoji_map.get(analysis_type, "🔄")

        type_name = "기술적 지표 분석" if analysis_type == "technical" else "뉴스 감정 분석"

        text = f"{emoji} {type_name} 시작..."

        attachments = [
            {
                "color": "36a64f",
                "title": f"{type_name} 진행 중",
                "text": "SMA, RSI, MACD 계산 중" if analysis_type == "technical" else "Alpha Vantage NEWS_SENTIMENT API 호출 중",
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        self._post_message(text, attachments, thread_ts)

    async def notify_analysis_complete(
        self,
        analysis_type: str,
        total_analyzed: int,
        recommended_count: int,
        thread_ts: Optional[str]
    ) -> None:
        """분석 완료 알림"""
        type_name = "기술적 분석" if analysis_type == "technical" else "뉴스 감정 분석"

        text = f"✅ {type_name} 완료"

        attachments = [
            {
                "color": "28a745",
                "title": f"{type_name} 성공",
                "fields": [
                    {"title": "분석 종목", "value": f"{total_analyzed}개", "short": True},
                    {"title": "추천 종목", "value": f"{recommended_count}개", "short": True},
                    {"title": "완료 시각", "value": datetime.now(KST).strftime("%Y-%m-%d %H:%M:%S"), "short": False},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        self._post_message(text, attachments, thread_ts)

    async def notify_analysis_error(
        self,
        analysis_type: str,
        error: str,
        thread_ts: Optional[str]
    ) -> None:
        """분석 오류 알림"""
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

        self._post_message(text, attachments, thread_ts)
