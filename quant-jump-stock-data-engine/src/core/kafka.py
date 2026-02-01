"""Kafka 이벤트 발행"""
import logging
from typing import Dict, Any
from src.events.publisher import publish_event

logger = logging.getLogger(__name__)


class KafkaEventPublisher:
    """Kafka 이벤트 발행 헬퍼"""

    @staticmethod
    def publish(event_type: str, data: Dict[str, Any]) -> None:
        """이벤트를 발행합니다."""
        try:
            publish_event(event_type, data)
            logger.info(f"Kafka 이벤트 발행 성공: {event_type}")
        except Exception as e:
            logger.error(f"Kafka 이벤트 발행 실패: {event_type} - {e}")
            raise
