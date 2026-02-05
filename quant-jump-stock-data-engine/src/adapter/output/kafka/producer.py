"""
Kafka Producer Adapter

이벤트를 Kafka로 발행하는 어댑터.
"""

import logging
import json
from typing import Dict, Any, Optional
from datetime import datetime
from confluent_kafka import Producer

from config.settings import Settings

logger = logging.getLogger(__name__)


def _json_serializer(obj):
    """datetime 객체를 ISO 형식으로 직렬화"""
    if isinstance(obj, datetime):
        return obj.isoformat()
    raise TypeError(f"Type {type(obj)} not serializable")


class KafkaProducerAdapter:
    """
    Kafka Producer 어댑터

    애플리케이션 이벤트를 Kafka로 발행.
    """

    def __init__(
        self,
        settings: Settings,
        client_id: str = "quantiq-data-engine"
    ):
        self.settings = settings
        self.client_id = client_id
        self._producer: Optional[Producer] = None

    def _get_producer(self) -> Producer:
        """Producer 인스턴스 반환 (Lazy init)"""
        if self._producer is None:
            conf = {
                'bootstrap.servers': self.settings.KAFKA_BOOTSTRAP_SERVERS,
                'client.id': self.client_id,
                'acks': 'all',
                'retries': 3,
                'retry.backoff.ms': 100,
                'batch.size': 16384,
                'linger.ms': 10,
                'compression.type': 'snappy',
                'enable.idempotence': True
            }
            self._producer = Producer(conf)
            logger.info(f"Kafka producer created: {self.settings.KAFKA_BOOTSTRAP_SERVERS}")
        return self._producer

    def _delivery_callback(self, err, msg):
        """전송 결과 콜백"""
        if err:
            logger.error(f"Message delivery failed: {err}")
        else:
            logger.debug(f"Message delivered to {msg.topic()} [{msg.partition()}]")

    def publish(self, event_type: str, data: Dict[str, Any]) -> None:
        """
        이벤트 발행

        Args:
            event_type: 이벤트 타입 (토픽 결정에 사용)
            data: 이벤트 페이로드
        """
        topic = self._resolve_topic(event_type)

        event = {
            "eventType": event_type,
            "eventId": f"{event_type}_{datetime.utcnow().timestamp()}",
            "timestamp": datetime.utcnow().isoformat(),
            "payload": data
        }

        try:
            producer = self._get_producer()
            message = json.dumps(event, default=_json_serializer).encode('utf-8')

            producer.produce(
                topic,
                message,
                callback=self._delivery_callback
            )
            producer.flush()

            logger.info(f"Event published: {event_type} → {topic}")

        except Exception as e:
            logger.error(f"Failed to publish event {event_type}: {e}")
            raise

    def _resolve_topic(self, event_type: str) -> str:
        """이벤트 타입에 따른 토픽 결정"""
        topic_mapping = {
            # 경제 데이터
            "ECONOMIC_DATA_UPDATED": "economic.data.updated",
            "ECONOMIC_DATA_UPDATE_FAILED": "economic.data.update.failed",
            # 기술적 분석
            "ANALYSIS_TECHNICAL_COMPLETED": "analysis.technical.completed",
            "ANALYSIS_TECHNICAL_FAILED": "analysis.technical.failed",
            # 감정 분석
            "ANALYSIS_SENTIMENT_COMPLETED": "analysis.sentiment.completed",
            "ANALYSIS_SENTIMENT_FAILED": "analysis.sentiment.failed",
            # 전략 실행
            "STRATEGY_EXECUTION_COMPLETED": "strategy.execution.completed",
            "STRATEGY_EXECUTION_FAILED": "strategy.execution.failed",
            # 매매 신호
            "TRADING_SIGNAL_GENERATED": "trading.signal.generated",
            # 백테스트 (SCRUM-186)
            "BACKTEST_COMPLETED": "backtest.results.completed",
            "BACKTEST_FAILED": "backtest.results.failed",
        }

        return topic_mapping.get(event_type, "data-engine.events")

    def close(self) -> None:
        """Producer 종료"""
        if self._producer:
            self._producer.flush()
            self._producer = None
            logger.info("Kafka producer closed")

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
        return False
