"""
Slack Bot Token API 클라이언트

Bot Token(xoxb-...)을 사용하여 chat.postMessage API 호출.
스레드 답글(thread_ts) 지원.
"""

import logging
import requests
from typing import Optional

logger = logging.getLogger(__name__)

SLACK_API_URL = "https://slack.com/api/chat.postMessage"


class SlackBotClient:
    """Slack Bot Token API 클라이언트 (스레드 답글 지원)"""

    def __init__(self, bot_token: str):
        self.bot_token = bot_token

    @property
    def is_available(self) -> bool:
        return bool(self.bot_token)

    def post_message(
        self,
        channel: str,
        text: str,
        blocks: list = None,
        attachments: list = None,
        thread_ts: Optional[str] = None,
    ) -> Optional[str]:
        """
        채널에 메시지 발송.

        Args:
            channel: 채널 ID 또는 이름 (#channel-name)
            text: 메시지 텍스트 (fallback)
            blocks: Block Kit blocks
            attachments: legacy attachments
            thread_ts: 스레드 답글일 때 부모 메시지의 ts

        Returns:
            메시지의 ts (thread_ts로 사용 가능). 실패 시 None.
        """
        if not self.bot_token or not channel:
            return None

        headers = {
            "Authorization": f"Bearer {self.bot_token}",
            "Content-Type": "application/json",
        }

        payload = {
            "channel": channel,
            "text": text,
        }
        if blocks:
            payload["blocks"] = blocks
        if attachments:
            payload["attachments"] = attachments
        if thread_ts:
            payload["thread_ts"] = thread_ts

        try:
            response = requests.post(
                SLACK_API_URL,
                headers=headers,
                json=payload,
                timeout=10,
            )
            data = response.json()

            if data.get("ok"):
                ts = data.get("ts")
                logger.debug(f"Slack Bot 메시지 발송 완료 (channel={channel}, ts={ts})")
                return ts
            else:
                error = data.get("error", "unknown")
                logger.error(f"Slack Bot API 에러: {error} (channel={channel})")
                return None

        except Exception as e:
            logger.error(f"Slack Bot 메시지 발송 실패: {e}")
            return None
