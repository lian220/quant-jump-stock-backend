"""
Pub/Sub Push Handler (Cloud Run용)

Cloud Run에서 Pub/Sub Push 메시지를 수신하는 FastAPI 라우터.
Push 구독은 Pub/Sub이 HTTP POST로 메시지를 전달하는 방식.

메시지 형식 (Pub/Sub Push):
{
  "message": {
    "data": "<base64 encoded JSON>",
    "messageId": "...",
    "publishTime": "..."
  },
  "subscription": "projects/.../subscriptions/..."
}
"""

import asyncio
import base64
import inspect
import json
import logging
from typing import Dict, Callable, Any, Union

from fastapi import APIRouter, Request, Response

from .subscriber import PubSubMessage

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/_ah/push-handler", tags=["pubsub-push"])

# 토픽별 핸들러 레지스트리 (main.py에서 등록)
_handlers: Dict[str, Callable[[PubSubMessage], None]] = {}


def register_push_handler(topic: str, handler: Callable[[PubSubMessage], None]) -> None:
    """Push 핸들러 등록"""
    _handlers[topic] = handler
    logger.debug(f"Push handler registered: {topic}")


def _pubsub_topic_to_dot_notation(pubsub_topic: str) -> str:
    """Pub/Sub 토픽명(hyphen)을 dot 표기법으로 변환
    예: 'economic-data-update-request' → 'economic.data.update.request'
    """
    return pubsub_topic.replace('-', '.')


def _parse_push_message(topic: str, payload: Dict[str, Any]) -> PubSubMessage:
    """Push 메시지 페이로드를 PubSubMessage로 변환"""
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


@router.post("/{topic_name}")
async def handle_push_message(topic_name: str, request: Request) -> Response:
    """
    Pub/Sub Push 메시지 수신 엔드포인트.

    - 200 OK → Pub/Sub이 ACK 처리 (성공)
    - 400/422 → Pub/Sub이 ACK 처리 (재시도 불가능한 오류)
    - 500 → Pub/Sub이 NACK 처리 (재시도)
    """
    try:
        envelope = await request.json()
    except Exception as e:
        logger.error(f"Invalid JSON body: {e}")
        return Response(status_code=400, content="Invalid JSON")

    # Pub/Sub push 메시지 구조 파싱
    message = envelope.get("message")
    if not message:
        logger.error("No 'message' field in push envelope")
        return Response(status_code=400, content="Missing message field")

    # base64 디코딩
    data_b64 = message.get("data", "")
    try:
        data_bytes = base64.b64decode(data_b64)
        data = json.loads(data_bytes.decode("utf-8"))
    except Exception as e:
        logger.error(f"Failed to decode push message data: {e}")
        return Response(status_code=400, content="Invalid message data")

    # payload 추출 (기존 pull 메시지와 동일한 구조)
    payload = data.get("payload", data)

    # 토픽명 변환 (hyphen → dot notation)
    dot_topic = _pubsub_topic_to_dot_notation(topic_name)

    # 핸들러 찾기
    handler = _handlers.get(dot_topic)
    if handler is None:
        logger.warning(f"No push handler for topic: {dot_topic} (raw: {topic_name})")
        return Response(status_code=200, content="No handler (ACK)")

    # 메시지 처리
    parsed = None
    try:
        parsed = _parse_push_message(dot_topic, payload)
        message_id = message.get("messageId", "unknown")
        logger.debug(f"Push message: topic={dot_topic}, messageId={message_id}, requestId={parsed.request_id}")

        if asyncio.iscoroutinefunction(handler):
            await handler(parsed)
        else:
            handler(parsed)

        logger.debug(f"Push message done: topic={dot_topic}, messageId={message_id}")
        return Response(status_code=200, content="OK")

    except Exception as e:
        # 재시도 가능 여부로 ACK/NACK 분기:
        #   - NonRetryableError(데이터/설정 오류 등) → 200 ACK (재시도해도 실패 → 영구 폐기)
        #   - 그 외 재시도성 에러(일시적 DB/네트워크/외부 API) → 500 NACK → Pub/Sub 재전송
        # 포이즌 메시지는 토픽 retention(24h) + backoff(10~600s)로 자연 종료, Slack 5분 dedup 으로 알림 스팸 방지.
        from .subscriber import NonRetryableError
        is_non_retryable = isinstance(e, NonRetryableError)
        request_id = parsed.request_id if parsed is not None else None

        if is_non_retryable:
            logger.error(f"Non-retryable error for {dot_topic} (ACK, 재시도 안함): {e}")
        else:
            logger.exception(f"Retryable error for {dot_topic} (NACK, 재시도): {e}")

        # Slack 에러 채널 알림 (실패해도 응답에 영향 X)
        try:
            from services.slack_notifier import SlackNotifier
            SlackNotifier.notify_handler_error(
                topic=dot_topic,
                error=str(e),
                error_type=type(e).__name__,
                request_id=request_id,
                retryable=not is_non_retryable,
            )
        except Exception as notify_err:
            logger.warning(f"Slack 에러 알림 실패: {notify_err}")

        if is_non_retryable:
            return Response(status_code=200, content="Non-retryable error (ACK)")
        return Response(status_code=500, content="Retryable error (NACK, will retry)")
