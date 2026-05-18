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
            logger.debug(f"Pub/Sub publisher created: {self._project_id}")
        return self._publisher

    def publish(self, event_type: str, data: Dict[str, Any]) -> None:
        """
        이벤트 발행.

        타임아웃 처리 (2026-05-15 21:07 사고 후 정착):
          - publish ack 대기 5초 (이전 30초 → cold start cascade 위험 차단)
          - TimeoutError 만 흡수: SDK 가 background 에서 retry 계속 (at-least-once),
            비즈니스 핸들러는 데이터 처리 완료 직후 호출이므로 raise 하면 false alarm.
            대신 Slack 운영 채널로 직접 알림 (rate-limit 적용).
          - 그 외 예외 (404/IAM/serialize) 는 raise → push_handler 글로벌 핸들러 알림.

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
            future.result(timeout=5)  # 5초 fail-fast (이전 30초)

            logger.debug(f"Event published: {event_type} -> {pubsub_topic}")

        except TimeoutError:
            # 2026-05-18 사용자 결정: publish timeout Slack 알림 제거.
            # SDK at-least-once retry 가 background 에서 진행되므로 메시지 누락 가능성 낮음.
            # 매 cron 발화마다 N개 토픽 publish timeout = N개 Slack 노이즈 → 차단.
            # 진짜 메시지 누락은 (1) 구독자 측 수신 카운터 metric (별도 task)
            # (2) 추천 산출 직전 pre-flight gate 로 감지.
            logger.warning(
                f"Pub/Sub publish timeout (>5s) for {event_type} → {pubsub_topic}. "
                f"SDK background retry 진행 중 (at-least-once eventual delivery 신뢰). "
                f"event_id={event['eventId']}"
            )
            # 흡수 — 비즈니스 핸들러는 이미 데이터 처리 완료 상태

        except Exception as e:
            logger.error(f"Failed to publish event {event_type}: {e}")
            raise

    def _resolve_topic(self, event_type: str) -> str:
        """
        이벤트 타입에 따른 토픽 결정

        Core API EventTopics (EventSchema.kt)와 동기화된 토픽명 사용.
        """
        topic_mapping = {
            # ── 파이프라인 체이닝 (핸들러 → 다음 단계 트리거) ──
            "TRIGGER_ECONOMIC_DATA": "economic.data.update.request",
            "TRIGGER_TECHNICAL_ANALYSIS": "analysis.technical.request",
            "TRIGGER_SENTIMENT_ANALYSIS": "analysis.sentiment.request",
            "TRIGGER_VERTEX_AI_PREDICTION": "vertex.ai.run.request",

            # ── 완료/실패 이벤트 (Core API EventTopics 기준) ──
            # 경제 데이터
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
            # News
            "NEWS_COLLECTED": "quantiq.news.collected",
            "NEWS_COLLECTION_FAILED": "quantiq.news.collection.failed",
        }

        topic = topic_mapping.get(event_type)
        if topic is None:
            logger.warning(
                f"알 수 없는 event_type '{event_type}', 기본 토픽 사용. "
                f"등록된 타입: {list(topic_mapping.keys())}"
            )
            return "data-engine-events"
        return topic

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
