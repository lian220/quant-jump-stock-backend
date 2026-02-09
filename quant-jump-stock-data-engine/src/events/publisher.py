"""
Event Publisher for Quantiq Data Engine

Kafka로 이벤트를 발행하는 범용 서비스입니다.
"""

from confluent_kafka import Producer
import json
import logging
from datetime import datetime
from core.config import settings
from events.schema import BaseEvent

logger = logging.getLogger(__name__)


def json_serializer(obj):
    """JSON 직렬화 헬퍼: datetime 객체를 ISO 형식 문자열로 변환"""
    if isinstance(obj, datetime):
        return obj.isoformat()
    raise TypeError(f"Type {type(obj)} not serializable")


class EventPublisher:
    """
    Kafka Event Publisher (Singleton Pattern)
    """
    _producer = None

    @classmethod
    def get_producer(cls):
        """Kafka Producer 인스턴스를 반환합니다 (Singleton)"""
        if cls._producer is None:
            try:
                conf = {
                    'bootstrap.servers': settings.KAFKA_BOOTSTRAP_SERVERS,
                    'client.id': 'quantiq-data-engine',
                    # Reliability settings
                    'acks': 'all',  # 모든 replica 확인
                    'retries': 3,  # 재시도 3회
                    'retry.backoff.ms': 100,  # 재시도 간격 100ms
                    # Performance settings
                    'batch.size': 16384,  # 16KB
                    'linger.ms': 10,  # 10ms 대기 후 배치 전송
                    'compression.type': 'snappy',  # 압축
                    # Idempotence for exactly-once semantics
                    'enable.idempotence': True
                }
                cls._producer = Producer(conf)
                logger.info(f"📡 Kafka producer created for {settings.KAFKA_BOOTSTRAP_SERVERS}")
            except Exception as e:
                logger.error(f"❌ Failed to create Kafka producer: {e}")
                raise
        return cls._producer

    @classmethod
    def delivery_report(cls, err, msg):
        """메시지 전송 결과 콜백"""
        if err is not None:
            logger.error(f'❌ Message delivery failed: {err}')
        else:
            logger.info(f'✅ Message delivered to {msg.topic()} [{msg.partition()}]')

    @classmethod
    def publish(cls, topic: str, event: BaseEvent):
        """
        이벤트를 Kafka 토픽에 발행합니다

        Args:
            topic: Kafka 토픽명
            event: 발행할 이벤트 (BaseEvent)
        """
        try:
            p = cls.get_producer()
            message = json.dumps(event.to_dict(), default=json_serializer).encode('utf-8')

            logger.info(f"📤 Publishing event to topic [{topic}]: eventId={event.eventId}, type={event.eventType}")
            logger.debug(f"Event payload: {message}")

            # Asynchronous produce
            p.produce(
                topic,
                message,
                callback=cls.delivery_report
            )

            # Flush to ensure delivery
            # In high throughput scenarios, this should be managed differently
            p.flush()

        except Exception as e:
            logger.error(f"❌ Failed to publish event to {topic}: {e}")
            import traceback
            logger.error(traceback.format_exc())

    @classmethod
    def close(cls):
        """Producer를 종료합니다"""
        if cls._producer is not None:
            cls._producer.flush()
            logger.info("📡 Kafka producer closed")


# ============================================================================
# Legacy Support
# ============================================================================

def publish_event(event_type: str, payload: dict):
    """
    Legacy 호환성을 위한 헬퍼 함수

    Deprecated: create_event()와 EventPublisher.publish()를 직접 사용하세요
    """
    from events.schema import create_event

    logger.warning("⚠️ publish_event() is deprecated. Use EventPublisher.publish() instead.")

    # Legacy event format을 BaseEvent로 변환
    event = BaseEvent(
        eventType=event_type,
        payload=payload
    )

    EventPublisher.publish(settings.KAFKA_TOPIC_ANALYSIS_COMPLETED, event)
