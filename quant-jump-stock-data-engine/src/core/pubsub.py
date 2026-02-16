"""Pub/Sub 이벤트 발행 헬퍼"""
import logging
from typing import Dict, Any

logger = logging.getLogger(__name__)


class PubSubEventPublisher:
    """Pub/Sub 이벤트 발행 헬퍼"""

    _publisher_adapter = None

    @classmethod
    def set_publisher(cls, publisher_adapter) -> None:
        """Publisher adapter 인스턴스를 설정합니다."""
        cls._publisher_adapter = publisher_adapter

    @staticmethod
    def publish(event_type: str, data: Dict[str, Any]) -> None:
        """이벤트를 발행합니다."""
        if PubSubEventPublisher._publisher_adapter is None:
            logger.warning("PubSubEventPublisher: publisher not initialized")
            return

        try:
            PubSubEventPublisher._publisher_adapter.publish(event_type, data)
            logger.debug(f"Pub/Sub 이벤트 발행: {event_type}")
        except Exception as e:
            logger.error(f"Pub/Sub 이벤트 발행 실패: {event_type} - {e}")
            raise
