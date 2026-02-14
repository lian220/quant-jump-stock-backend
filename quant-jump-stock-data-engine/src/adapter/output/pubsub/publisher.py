"""
Pub/Sub Publisher Adapter

이벤트를 Google Cloud Pub/Sub으로 발행하는 어댑터.
"""

import logging
import json
import uuid
from typing import Dict, Any, Optional
from datetime import datetime, timezone
from google.cloud import pubsub_v1

from config.settings import Settings

logger = logging.getLogger(__name__)


def _json_serializer(obj):
    """datetime 객체를 ISO 형식으로 직렬화"""
    if isinstance(obj, datetime):
        return obj.isoformat()
    raise TypeError(f"Type {type(obj)} not serializable")


def to_pubsub_topic(topic: str) -> str:
    """dot 표기법 토픽을 Pub/Sub 토픽명으로 변환"""
    return topic.replace('.', '-')


class PubSubPublisherAdapter:
    """
    Pub/Sub Publisher 어댑터

    애플리케이션 이벤트를 Pub/Sub으로 발행.
    EventPublisherProtocol과 동일한 publish() 인터페이스 제공.
    """

    def __init__(self, settings: Settings):
        self.settings = settings
        self._project_id = settings.pubsub.project_id
        self._publisher: Optional[pubsub_v1.PublisherClient] = None

    def _get_publisher(self) -> pubsub_v1.PublisherClient:
        """Publisher 인스턴스 반환 (Lazy init)"""
        if self._publisher is None:
            self._publisher = pubsub_v1.PublisherClient()
            logger.info(f"Pub/Sub publisher created for project: {self._project_id}")
        return self._publisher

    def publish(self, event_type: str, data: Dict[str, Any]) -> None:
        """
        이벤트 발행

        Args:
            event_type: 이벤트 타입 (토픽 결정에 사용)
            data: 이벤트 페이로드
        """
        topic = self._resolve_topic(event_type)
        pubsub_topic = to_pubsub_topic(topic)

        event = {
            "eventType": event_type,
            "eventId": str(uuid.uuid4()),
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "payload": data
        }

        try:
            publisher = self._get_publisher()
            message = json.dumps(event, default=_json_serializer).encode('utf-8')
            topic_path = publisher.topic_path(self._project_id, pubsub_topic)

            future = publisher.publish(topic_path, message)
            future.result(timeout=30)  # 발행 완료 대기 (30초 타임아웃)

            logger.info(f"Event published: {event_type} -> {pubsub_topic}")

        except Exception as e:
            logger.error(f"Failed to publish event {event_type}: {e}")
            raise

    def _resolve_topic(self, event_type: str) -> str:
        """
        이벤트 타입에 따른 토픽 결정

        Core API EventTopics (EventSchema.kt)와 동기화된 토픽명 사용.
        """
        topic_mapping = {
            # 경제 데이터 (Core API EventTopics 기준)
            "ECONOMIC_DATA_UPDATED": "quantiq.economic.data.updated",
            "ECONOMIC_DATA_UPDATE_FAILED": "quantiq.economic.data.sync.failed",
            # 분석 완료 (기술적/감정 분석 모두 동일 토픽으로 발행)
            "ANALYSIS_TECHNICAL_COMPLETED": "quantiq.analysis.completed",
            "ANALYSIS_TECHNICAL_FAILED": "analysis.technical.failed",
            "ANALYSIS_SENTIMENT_COMPLETED": "quantiq.analysis.completed",
            "ANALYSIS_SENTIMENT_FAILED": "analysis.sentiment.failed",
            # 종목 추천
            "STOCK_RECOMMENDATION_COMPLETED": "quantiq.analysis.completed",
            "STOCK_RECOMMENDATION_FAILED": "analysis.recommendation.failed",
            # 전략 실행
            "STRATEGY_EXECUTION_COMPLETED": "strategy.execution.completed",
            "STRATEGY_EXECUTION_FAILED": "strategy.execution.failed",
            # 매매 신호
            "TRADING_SIGNAL_GENERATED": "quantiq.trading.signal.detected",
            # 백테스트
            "BACKTEST_COMPLETED": "quantiq.backtest.completed",
            "BACKTEST_FAILED": "quantiq.backtest.failed",
            # Vertex AI
            "VERTEX_AI_JOB_SUBMITTED": "vertex.ai.run.submitted",
            "VERTEX_AI_JOB_FAILED": "vertex.ai.run.failed",
        }

        return topic_mapping.get(event_type, "data-engine-events")

    def close(self) -> None:
        """Publisher 종료"""
        if self._publisher:
            self._publisher.stop()
            self._publisher = None
            logger.info("Pub/Sub publisher closed")

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
        return False
