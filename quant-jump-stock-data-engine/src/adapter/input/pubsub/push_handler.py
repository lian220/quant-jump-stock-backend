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
        # NonRetryableError 포함 모든 예외를 구분
        from .subscriber import NonRetryableError
        if isinstance(e, NonRetryableError):
            logger.error(f"Non-retryable error for {dot_topic} (ACK): {e}")
            return Response(status_code=200, content="Non-retryable error (ACK)")

        logger.exception(f"Handler error for {dot_topic} (ACK처리, 재시도 안함): {e}")
        return Response(status_code=200, content="Handler error (ACK, no retry)")
