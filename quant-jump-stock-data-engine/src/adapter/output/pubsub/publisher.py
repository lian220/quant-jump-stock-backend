"""
Pub/Sub Publisher Adapter

이벤트를 Google Cloud Pub/Sub으로 발행하는 어댑터.

2026-05-18 사고 우회 — REST API 직접 호출로 전환:
  - 2026-05-15부터 Cloud Run 컨테이너 안에서 google-cloud-pubsub SDK의
    gRPC publish가 stall (5~30초 ack 미수신).
  - 로컬 gcloud → publish 즉시 ack (0.8초). GCP 인프라 자체는 정상.
  - Cloud Run egress의 HTTPS(Slack, MongoDB)는 정상 → gRPC channel만 문제.
  - SDK의 `transport='rest'` 옵션은 high-level PublisherClient 미지원이라
    `google-auth` + `requests` 로 REST API 직접 호출.

진단 근거 — `gcloud logging read` 결과:
  - 5/13 (image b06472): publish ack 즉시
  - 5/15+ (image 6b55f7~41fd67): 5~30초 timeout
  - poetry.lock 무변경 → SDK 버전 동일 → SDK 자체 문제 아님
  - IAM 변경 0건 → 권한 문제 아님
"""

import base64
import logging
import json
import uuid
from typing import Dict, Any, Optional
from datetime import datetime, timezone

import google.auth
import requests
from google.auth.transport.requests import AuthorizedSession

from config.settings import Settings

logger = logging.getLogger(__name__)

_PUBSUB_API_BASE = "https://pubsub.googleapis.com/v1"
_DEFAULT_TIMEOUT = 10  # HTTPS publish ack 대기


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
        self._session: Optional[AuthorizedSession] = None

    def _get_session(self) -> AuthorizedSession:
        """HTTPS publish용 인증 세션 (Lazy init).

        google.auth.default() 가 Cloud Run metadata server 또는 ADC에서 credentials 자동 발견.
        AuthorizedSession은 토큰 자동 갱신 + requests 호환.
        """
        if self._session is None:
            credentials, _ = google.auth.default(
                scopes=["https://www.googleapis.com/auth/pubsub"]
            )
            self._session = AuthorizedSession(credentials)
            logger.info(
                f"Pub/Sub REST session created: project={self._project_id}"
            )
        return self._session

    def publish(self, event_type: str, data: Dict[str, Any]) -> None:
        """
        이벤트 발행 — REST API 직접 호출 (gRPC 우회).

        흐름:
          1. POST https://pubsub.googleapis.com/v1/projects/{p}/topics/{t}:publish
          2. body: {"messages": [{"data": <base64>}]}
          3. ack 대기 _DEFAULT_TIMEOUT (10초)

        에러 처리:
          - requests.Timeout / ConnectionError → 흡수 + WARNING (비즈니스 raise 차단)
          - HTTP 4xx/5xx → raise (404/IAM/serialize 등 즉시 실패)

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

        message_bytes = json.dumps(event, default=_json_serializer).encode('utf-8')
        encoded = base64.b64encode(message_bytes).decode('ascii')
        url = f"{_PUBSUB_API_BASE}/projects/{self._project_id}/topics/{pubsub_topic}:publish"
        body = {"messages": [{"data": encoded}]}

        try:
            session = self._get_session()
            resp = session.post(url, json=body, timeout=_DEFAULT_TIMEOUT)
            resp.raise_for_status()
            message_ids = resp.json().get("messageIds", [])
            logger.debug(
                f"Event published (REST): {event_type} → {pubsub_topic} "
                f"messageIds={message_ids}"
            )

        except (requests.exceptions.Timeout, requests.exceptions.ConnectionError) as e:
            # 일시적 네트워크 이슈 — 흡수. 비즈니스 핸들러는 이미 데이터 처리 완료 상태.
            # 진짜 메시지 누락 감지는 pre-flight gate(추천 산출 직전) + 구독자 수신 metric.
            logger.warning(
                f"Pub/Sub REST publish 네트워크 이슈 흡수 — {event_type} → {pubsub_topic} "
                f"event_id={event['eventId']} err={type(e).__name__}: {e}"
            )

        except requests.exceptions.HTTPError as e:
            # 404 (토픽 없음) / 403 (IAM) / 400 (페이로드 오류) 등 — raise → push_handler 알림
            logger.error(
                f"Failed to publish event {event_type}: HTTP {resp.status_code} {resp.text[:200]}"
            )
            raise

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
        """Publisher 세션 종료"""
        if self._session:
            self._session.close()
            self._session = None
            logger.info("Pub/Sub REST session closed")

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
        return False
