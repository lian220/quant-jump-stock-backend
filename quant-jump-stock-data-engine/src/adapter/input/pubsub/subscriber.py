"""
Pub/Sub Subscriber Adapter

Google Cloud Pub/Sub 메시지를 수신하여 핸들러로 라우팅하는 어댑터.
"""

import logging
import json
import asyncio
from typing import Dict, List, Optional, Callable, Any
from dataclasses import dataclass
from google.cloud import pubsub_v1
from concurrent.futures import TimeoutError

from config.settings import Settings

logger = logging.getLogger(__name__)


class NonRetryableError(Exception):
    """재시도해도 성공할 수 없는 에러 (403, 설정 오류, 유효성 검증 실패 등)

    이 예외를 raise하면 subscriber가 메시지를 ACK 처리하여
    무한 재시도 폭풍을 방지합니다.
    """
    pass


def to_pubsub_subscription(topic: str) -> str:
    """dot 표기법 토픽을 Pub/Sub 구독명으로 변환
    예: 'economic.data.update.request' → 'economic-data-update-request-sub'
    """
    return topic.replace('.', '-') + "-sub"


def to_pubsub_topic(topic: str) -> str:
    """dot 표기법 토픽을 Pub/Sub 토픽명으로 변환
    예: 'economic.data.update.request' → 'economic-data-update-request'
    """
    return topic.replace('.', '-')


@dataclass
class PubSubMessage:
    """파싱된 Pub/Sub 메시지"""
    topic: str
    payload: Dict[str, Any]
    request_id: str
    thread_ts: Optional[str] = None
    target_date: Optional[str] = None
    start_date: Optional[str] = None
    end_date: Optional[str] = None
    source: str = "standalone"  # "standalone" | "pipeline"


class PubSubSubscriberAdapter:
    """
    Pub/Sub Subscriber 어댑터

    토픽별 핸들러를 등록하고 메시지를 라우팅합니다.
    Streaming pull 방식으로 메시지를 수신합니다.
    """

    def __init__(self, settings: Settings):
        self.settings = settings
        self._project_id = settings.pubsub.project_id
        self._subscriber = pubsub_v1.SubscriberClient()
        self._handlers: Dict[str, Callable[[PubSubMessage], None]] = {}
        self._streaming_futures = []
        self._running = False

    def register_handler(
        self,
        topic: str,
        handler: Callable[[PubSubMessage], None]
    ) -> None:
        """토픽에 핸들러 등록"""
        self._handlers[topic] = handler
        logger.debug(f"Handler registered: {topic}")

    def _parse_message(self, topic: str, data: bytes) -> Optional[PubSubMessage]:
        """메시지 파싱"""
        try:
            value = json.loads(data.decode('utf-8'))
            payload = value.get("payload", value)

            return PubSubMessage(
                topic=topic,
                payload=payload,
                request_id=payload.get("requestId", "unknown"),
                thread_ts=payload.get("threadTs"),
                target_date=payload.get("targetDate"),
                start_date=payload.get("startDate"),
                end_date=payload.get("endDate"),
                source=payload.get("source", "standalone"),
            )
        except json.JSONDecodeError as e:
            logger.error(f"Failed to parse message: {e}")
            return None
        except Exception as e:
            logger.error(f"Unexpected error parsing message: {e}")
            return None

    def _make_callback(self, topic: str):
        """토픽별 콜백 함수 생성"""
        def callback(message: pubsub_v1.subscriber.message.Message):
            parsed = self._parse_message(topic, message.data)
            if parsed is None:
                message.ack()
                return

            handler = self._handlers.get(topic)
            if handler is None:
                logger.warning(f"No handler for topic: {topic}")
                message.ack()
                return

            try:
                logger.debug(f"Processing message: {topic}, requestId={parsed.request_id}")
                # async handler 실행
                if asyncio.iscoroutinefunction(handler):
                    asyncio.run(handler(parsed))
                else:
                    # 동기 handler
                    handler(parsed)
                message.ack()
            except NonRetryableError as e:
                logger.error(f"Non-retryable error for {topic} (ACK처리, 재시도 안함): {e}")
                message.ack()
            except Exception as e:
                logger.exception(f"Handler error for {topic} (ACK처리, 재시도 안함): {e}")
                message.ack()

        return callback

    def start(self, topics: Optional[List[str]] = None) -> None:
        """Subscriber 시작 (streaming pull)"""
        if topics is None:
            topics = list(self._handlers.keys())

        if not topics:
            logger.warning("No topics to subscribe")
            return

        self._running = True

        for topic in topics:
            subscription_name = to_pubsub_subscription(topic)
            subscription_path = self._subscriber.subscription_path(
                self._project_id, subscription_name
            )

            future = self._subscriber.subscribe(
                subscription_path,
                callback=self._make_callback(topic)
            )
            self._streaming_futures.append(future)
            logger.info(f"Pub/Sub 구독 시작: {subscription_path}")

        logger.info(f"Pub/Sub subscriber started. Subscribed topics: {topics}")

    def run_forever(self) -> None:
        """스트리밍 풀을 유지하며 메시지 처리"""
        logger.info("Starting subscriber streaming pull...")
        try:
            for future in self._streaming_futures:
                future.result()
        except TimeoutError:
            logger.warning("Subscriber timed out")
        except KeyboardInterrupt:
            logger.info("Subscriber interrupted")

    def stop(self) -> None:
        """Subscriber 종료"""
        self._running = False
        for future in self._streaming_futures:
            future.cancel()
        self._streaming_futures.clear()
        self._subscriber.close()
        logger.info("Pub/Sub subscriber stopped")

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.stop()
        return False
