import requests
import logging
from datetime import datetime
from pytz import timezone
from src.core.config import settings
from typing import Optional, Dict

KST = timezone('Asia/Seoul')

logger = logging.getLogger(__name__)


class SlackNotifier:
    """Slack 알림 서비스 (Thread 지원) - Slack API 기반"""

    # Request별 thread_ts 저장소
    _thread_timestamps: Dict[str, str] = {}

    @staticmethod
    def _use_api() -> bool:
        """Slack API 사용 가능 여부"""
        return bool(settings.SLACK_BOT_TOKEN)

    @staticmethod
    def _post_message(text: str, attachments: list = None, thread_ts: Optional[str] = None) -> Optional[str]:
        """
        Slack API 또는 Webhook으로 메시지 전송

        Args:
            text: 메시지 텍스트
            attachments: 첨부 파일
            thread_ts: 스레드 타임스탬프 (thread 답글용)

        Returns:
            메시지 타임스탬프 (API 사용 시) 또는 None (Webhook 사용 시)
        """
        if settings.SLACK_BOT_TOKEN:
            # Slack API 사용 (권장)
            return SlackNotifier._post_via_api(text, attachments, thread_ts)
        elif settings.SLACK_WEBHOOK_URL:
            # Slack Webhook 사용 (대체)
            return SlackNotifier._post_via_webhook(text, attachments)
        else:
            logger.warning("Slack configuration not found (need SLACK_BOT_TOKEN or SLACK_WEBHOOK_URL)")
            return None

    @staticmethod
    def _post_via_api(text: str, attachments: list = None, thread_ts: Optional[str] = None) -> Optional[str]:
        """Slack API (chat.postMessage)를 사용해 메시지 전송"""
        try:
            headers = {
                "Authorization": f"Bearer {settings.SLACK_BOT_TOKEN}",
                "Content-Type": "application/json"
            }

            payload = {
                "channel": settings.SLACK_CHANNEL,
                "text": text,
            }

            if attachments:
                payload["attachments"] = attachments

            if thread_ts:
                payload["thread_ts"] = thread_ts

            response = requests.post(
                "https://slack.com/api/chat.postMessage",
                headers=headers,
                json=payload,
                timeout=5
            )
            response.raise_for_status()

            data = response.json()
            if data.get("ok"):
                message_ts = data.get("ts")
                if thread_ts:
                    logger.info(f"✅ Slack 스레드 답글 발송: thread_ts={thread_ts}, ts={message_ts}")
                else:
                    logger.info(f"✅ Slack 메시지 발송: ts={message_ts}")
                return message_ts
            else:
                error_msg = data.get("error", "Unknown error")
                logger.error(f"❌ Slack API 오류: {error_msg}")
                return None

        except Exception as e:
            logger.error(f"❌ Slack 메시지 발송 실패: {e}")
            return None

    @staticmethod
    def _post_via_webhook(text: str, attachments: list = None) -> Optional[str]:
        """Slack Webhook을 사용해 메시지 전송 (Thread 미지원)"""
        try:
            message = {
                "text": text,
            }

            if attachments:
                message["attachments"] = attachments

            response = requests.post(
                settings.SLACK_WEBHOOK_URL,
                json=message,
                timeout=5
            )
            response.raise_for_status()

            logger.info("✅ Slack 메시지 발송 (Webhook)")
            return None

        except Exception as e:
            logger.error(f"❌ Slack 메시지 발송 실패: {e}")
            return None

    @staticmethod
    def notify_economic_data_collection_start(request_id: str, source: str = "kafka", parent_thread_ts: Optional[str] = None) -> Optional[str]:
        """
        경제 데이터 수집 시작 알림 (스레드 답글)

        Args:
            request_id: 요청 ID
            source: 데이터 소스
            parent_thread_ts: 부모 스레드 타임스탬프 (Kotlin에서 전달받음)

        Returns:
            메시지 타임스탬프
        """
        # Kotlin에서 전달받은 parent_thread_ts가 있으면 저장
        if parent_thread_ts:
            SlackNotifier._thread_timestamps[request_id] = parent_thread_ts
            logger.info(f"📌 Kotlin 루트 스레드 연결: request_id={request_id}, thread_ts={parent_thread_ts}")

        text = "🔄 경제 데이터 수집 시작"
        attachments = [
            {
                "color": "0099cc",
                "title": "데이터 수집 진행 중",
                "text": "경제 데이터 수집이 시작되었습니다.",
                "fields": [
                    {"title": "Request ID", "value": request_id, "short": True},
                    {"title": "Source", "value": source, "short": True},
                    {"title": "Timestamp", "value": datetime.now(KST).isoformat(), "short": True},
                    {"title": "Status", "value": "🔄 In Progress", "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        # 답글로 발송 (thread_ts 포함)
        thread_ts = SlackNotifier._thread_timestamps.get(request_id)
        message_ts = SlackNotifier._post_message(text, attachments, thread_ts=thread_ts)

        if not thread_ts and not settings.SLACK_BOT_TOKEN:
            logger.warning(f"⚠️ 스레드 답글 불가: Bot Token 없음 (request_id={request_id})")

        return message_ts

    @staticmethod
    def notify_economic_data_collection_success(request_id: str, data_summary: dict = None, thread_ts: Optional[str] = None):
        """
        경제 데이터 수집 완료 알림 (스레드 답글)

        Args:
            request_id: 요청 ID
            data_summary: 데이터 요약
            thread_ts: 스레드 타임스탬프 (명시적 전달 가능)
        """
        if data_summary is None:
            data_summary = {}

        # 명시적으로 전달받은 thread_ts가 없으면 저장된 것 조회
        if not thread_ts:
            thread_ts = SlackNotifier._thread_timestamps.get(request_id)

        if not thread_ts and not settings.SLACK_BOT_TOKEN:
            logger.warning(f"⚠️ 스레드 답글 불가: Bot Token 없음 (request_id={request_id})")

        # 수집 결과 데이터 추출
        fred_count = data_summary.get("fred_collected", 0)
        yahoo_count = data_summary.get("yahoo_collected", 0)
        total_count = data_summary.get("total_indicators", fred_count + yahoo_count)
        duration = data_summary.get("duration", "N/A")

        text = "✅ 경제 데이터 수집 완료"
        attachments = [
            {
                "color": "28a745",
                "title": "📊 수집 결과 요약",
                "text": f"총 {total_count}개 지표 수집 완료 (FRED: {fred_count}, Yahoo: {yahoo_count})",
                "fields": [
                    {"title": "Request ID", "value": request_id, "short": True},
                    {"title": "소요 시간", "value": duration, "short": True},
                    {"title": "FRED 지표", "value": f"{fred_count}개", "short": True},
                    {"title": "Yahoo Finance", "value": f"{yahoo_count}개", "short": True},
                    {"title": "총 수집 지표", "value": f"{total_count}개", "short": True},
                    {"title": "완료 시각", "value": datetime.now(KST).strftime("%Y-%m-%d %H:%M:%S"), "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        # 답글 발송 (thread_ts 포함)
        SlackNotifier._post_message(text, attachments, thread_ts=thread_ts)

        # 정리
        if request_id in SlackNotifier._thread_timestamps:
            del SlackNotifier._thread_timestamps[request_id]

    @staticmethod
    def notify_economic_data_collection_error(request_id: str, error: str, thread_ts: Optional[str] = None):
        """
        경제 데이터 수집 오류 알림 (스레드 답글)

        Args:
            request_id: 요청 ID
            error: 오류 메시지
            thread_ts: 스레드 타임스탬프 (명시적 전달 가능)
        """
        # 명시적으로 전달받은 thread_ts가 없으면 저장된 것 조회
        if not thread_ts:
            thread_ts = SlackNotifier._thread_timestamps.get(request_id)

        if not thread_ts and not settings.SLACK_BOT_TOKEN:
            logger.warning(f"⚠️ 스레드 답글 불가: Bot Token 없음 (request_id={request_id})")

        text = "⚠️ 경제 데이터 수집 오류"
        attachments = [
            {
                "color": "dc3545",
                "title": "경제 데이터 수집 실패",
                "text": "경제 데이터 수집 중 오류가 발생했습니다.",
                "fields": [
                    {"title": "Request ID", "value": request_id, "short": True},
                    {"title": "Error", "value": error, "short": False},
                    {"title": "Timestamp", "value": datetime.now(KST).isoformat(), "short": True},
                    {"title": "Action", "value": "로그를 확인하고 수동 재시도를 고려하세요", "short": False},
                    {"title": "Status", "value": "❌ Failed", "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        # 답글 발송 (thread_ts 포함)
        SlackNotifier._post_message(text, attachments, thread_ts=thread_ts)

        # 정리
        if request_id in SlackNotifier._thread_timestamps:
            del SlackNotifier._thread_timestamps[request_id]

    @staticmethod
    def notify_fred_api_error(indicator_code: str, error: str):
        """FRED API 오류 알림"""
        text = "⚠️ FRED API 오류"
        attachments = [
            {
                "color": "ffc107",
                "title": "FRED API 호출 실패",
                "text": f"경제 지표 {indicator_code} 수집에 실패했습니다.",
                "fields": [
                    {"title": "Indicator", "value": indicator_code, "short": True},
                    {"title": "Error", "value": error, "short": True},
                    {"title": "Timestamp", "value": datetime.now(KST).isoformat(), "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]
        SlackNotifier._post_message(text, attachments)

    @staticmethod
    def notify_yahoo_finance_error(ticker: str, error: str):
        """Yahoo Finance 오류 알림"""
        text = "⚠️ Yahoo Finance 오류"
        attachments = [
            {
                "color": "ffc107",
                "title": "Yahoo Finance 호출 실패",
                "text": f"시장 지표 {ticker} 수집에 실패했습니다.",
                "fields": [
                    {"title": "Ticker", "value": ticker, "short": True},
                    {"title": "Error", "value": error, "short": True},
                    {"title": "Timestamp", "value": datetime.now(KST).isoformat(), "short": True},
                ],
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]
        SlackNotifier._post_message(text, attachments)

    @staticmethod
    def send_thread_message(text: str, thread_ts: str):
        """
        간단한 스레드 답글 메시지 발송

        Args:
            text: 메시지 텍스트
            thread_ts: 스레드 타임스탬프
        """
        if not thread_ts:
            logger.warning("⚠️ thread_ts가 없어 스레드 답글을 보낼 수 없습니다")
            return

        attachments = [
            {
                "color": "0099cc",
                "text": text,
                "footer": "Quantiq Data Engine",
                "ts": int(datetime.now(KST).timestamp())
            }
        ]

        SlackNotifier._post_message("", attachments, thread_ts=thread_ts)
