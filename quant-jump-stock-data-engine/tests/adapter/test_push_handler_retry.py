"""Pub/Sub Push 핸들러 재시도 분기 테스트 (할일 HIGH #1 수정).

  - 재시도성 에러        → 500 (Pub/Sub NACK → 자동 재시도)
  - NonRetryableError    → 200 (ACK, 재시도 안 함)
  - 성공 / 핸들러 없음    → 200
  - 잘못된 JSON          → 400

라우트 함수(handle_push_message)를 직접 호출 — HTTP 레이어 없이 ACK/NACK status code 검증.
"""

import asyncio
import base64
import json

import pytest

from adapter.input.pubsub import push_handler
from adapter.input.pubsub.subscriber import NonRetryableError

TOPIC_RAW = "economic-data-update-request"
TOPIC_DOT = "economic.data.update.request"

_INVALID = object()


class _FakeRequest:
    """request.json() 만 흉내내는 최소 Fake."""

    def __init__(self, body):
        self._body = body

    async def json(self):
        if self._body is _INVALID:
            raise ValueError("invalid json body")
        return self._body


def _envelope(payload: dict, message_id: str = "m-1") -> dict:
    data = base64.b64encode(json.dumps(payload).encode()).decode()
    return {"message": {"data": data, "messageId": message_id}}


def _post(topic_raw: str, body) -> int:
    resp = asyncio.run(push_handler.handle_push_message(topic_raw, _FakeRequest(body)))
    return resp.status_code


@pytest.fixture(autouse=True)
def _reset_registry():
    push_handler._handlers.clear()
    yield
    push_handler._handlers.clear()


def test_retryable_error_returns_500_nack():
    push_handler.register_push_handler(
        TOPIC_DOT, lambda msg: (_ for _ in ()).throw(RuntimeError("transient DB error"))
    )
    assert _post(TOPIC_RAW, _envelope({"requestId": "r1"})) == 500  # NACK → 재시도


def test_non_retryable_error_returns_200_ack():
    push_handler.register_push_handler(
        TOPIC_DOT, lambda msg: (_ for _ in ()).throw(NonRetryableError("bad data"))
    )
    assert _post(TOPIC_RAW, _envelope({"requestId": "r1"})) == 200  # ACK → 재시도 안 함


def test_success_returns_200():
    seen = []
    push_handler.register_push_handler(TOPIC_DOT, lambda msg: seen.append(msg.request_id))
    assert _post(TOPIC_RAW, _envelope({"requestId": "r1"})) == 200
    assert seen == ["r1"]


def test_no_handler_returns_200_ack():
    assert _post("unknown-topic", _envelope({"requestId": "r1"})) == 200


def test_invalid_json_returns_400():
    assert _post(TOPIC_RAW, _INVALID) == 400
