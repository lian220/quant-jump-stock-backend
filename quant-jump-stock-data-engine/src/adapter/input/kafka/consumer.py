"""
Kafka Consumer Adapter

Kafka 메시지를 수신하여 핸들러로 라우팅하는 어댑터.
"""

import logging
import json
from typing import Dict, List, Optional, Callable, Any
from dataclasses import dataclass
from confluent_kafka import Consumer, KafkaError, Message

from config.settings import Settings

logger = logging.getLogger(__name__)


@dataclass
class KafkaMessage:
    """파싱된 Kafka 메시지"""
    topic: str
    payload: Dict[str, Any]
    request_id: str
    thread_ts: Optional[str] = None
    target_date: Optional[str] = None
    raw_message: Optional[Message] = None


class KafkaConsumerAdapter:
    """
    Kafka Consumer 어댑터

    토픽별 핸들러를 등록하고 메시지를 라우팅합니다.
    """

    def __init__(
        self,
        settings: Settings,
        group_id: str = "quantiq-data-engine",
        auto_offset_reset: str = "earliest"
    ):
        self.settings = settings
        self.group_id = group_id
        self.auto_offset_reset = auto_offset_reset
        self._consumer: Optional[Consumer] = None
        self._handlers: Dict[str, Callable[[KafkaMessage], None]] = {}
        self._running = False

    def register_handler(
        self,
        topic: str,
        handler: Callable[[KafkaMessage], None]
    ) -> None:
        """
        토픽에 핸들러 등록

        Args:
            topic: Kafka 토픽명
            handler: 메시지 처리 함수
        """
        self._handlers[topic] = handler
        logger.info(f"Handler registered for topic: {topic}")

    def _create_consumer(self) -> Consumer:
        """Consumer 인스턴스 생성"""
        conf = {
            'bootstrap.servers': self.settings.KAFKA_BOOTSTRAP_SERVERS,
            'group.id': self.group_id,
            'auto.offset.reset': self.auto_offset_reset,
            'enable.auto.commit': True,
            'auto.commit.interval.ms': 5000,
        }
        return Consumer(conf)

    def _parse_message(self, msg: Message) -> Optional[KafkaMessage]:
        """메시지 파싱"""
        try:
            topic = msg.topic()
            value = json.loads(msg.value().decode('utf-8'))

            # payload 필드 추출 (중첩된 경우 처리)
            payload = value.get("payload", value)

            return KafkaMessage(
                topic=topic,
                payload=payload,
                request_id=payload.get("requestId", "unknown"),
                thread_ts=payload.get("threadTs"),
                target_date=payload.get("targetDate"),
                raw_message=msg
            )
        except json.JSONDecodeError as e:
            logger.error(f"Failed to parse message: {e}")
            return None
        except Exception as e:
            logger.error(f"Unexpected error parsing message: {e}")
            return None

    def start(self, topics: Optional[List[str]] = None) -> None:
        """
        Consumer 시작

        Args:
            topics: 구독할 토픽 목록 (None이면 등록된 핸들러의 토픽 사용)
        """
        if topics is None:
            topics = list(self._handlers.keys())

        if not topics:
            logger.warning("No topics to subscribe")
            return

        self._consumer = self._create_consumer()
        self._consumer.subscribe(topics)
        self._running = True

        logger.info(f"Kafka consumer started. Subscribed topics: {topics}")

    def poll_once(self, timeout: float = 1.0) -> Optional[KafkaMessage]:
        """
        단일 메시지 폴링

        Args:
            timeout: 폴링 타임아웃 (초)

        Returns:
            파싱된 메시지 또는 None
        """
        if self._consumer is None:
            return None

        msg = self._consumer.poll(timeout)

        if msg is None:
            return None

        if msg.error():
            if msg.error().code() == KafkaError._PARTITION_EOF:
                return None
            logger.error(f"Consumer error: {msg.error()}")
            return None

        return self._parse_message(msg)

    def process_message(self, message: KafkaMessage) -> bool:
        """
        메시지를 적절한 핸들러로 라우팅

        Args:
            message: 파싱된 Kafka 메시지

        Returns:
            처리 성공 여부
        """
        handler = self._handlers.get(message.topic)

        if handler is None:
            logger.warning(f"No handler for topic: {message.topic}")
            return False

        try:
            logger.info(f"Processing message from {message.topic}, requestId={message.request_id}")
            handler(message)
            return True
        except Exception as e:
            logger.exception(f"Handler error for {message.topic}: {e}")
            return False

    def run_forever(self, poll_timeout: float = 1.0) -> None:
        """
        무한 루프로 메시지 처리

        Args:
            poll_timeout: 폴링 타임아웃 (초)
        """
        logger.info("Starting consumer loop...")

        while self._running:
            message = self.poll_once(poll_timeout)
            if message:
                self.process_message(message)

    def stop(self) -> None:
        """Consumer 종료"""
        self._running = False
        if self._consumer:
            self._consumer.close()
            self._consumer = None
        logger.info("Kafka consumer stopped")

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.stop()
        return False
