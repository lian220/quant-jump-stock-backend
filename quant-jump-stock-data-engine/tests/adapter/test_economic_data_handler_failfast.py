"""경제 데이터 핸들러가 저장 실패를 성공으로 보고하지 않는지 검증.

배경 (2026-08-13 Atlas 용량 초과 사고):
  서비스가 success: False 를 반환하더라도 핸들러가 그 값을 읽지 않으면 의미가 없다.
  사고 당시 핸들러는 result["success"] 를 검사하지 않고 무조건 성공 알림 + 다음
  단계 체이닝을 수행했다. 서비스 fail-fast 와 짝이 되는 방어선이다.

  저장 실패는 재시도로 풀리지 않는다(용량 한도/권한). 재시도 폭주를 만들지 않도록
  NonRetryableError 로 처리한다 — 2026-06-17 재전송 5회 사고의 교훈.
"""
import pytest

from adapter.input.pubsub.handlers import EconomicDataHandler
from adapter.input.pubsub.subscriber import NonRetryableError, PubSubMessage


class _FakeService:
    def __init__(self, result):
        self._result = result

    def collect_economic_data(self, start_date=None, end_date=None):
        return self._result


class _FakeNotifier:
    def __init__(self):
        self.errors = []
        self.successes = []

    def notify_start(self, *a, **k):
        return None

    def notify_success(self, request_id, summary, thread_ts=None):
        self.successes.append(summary)

    def notify_error(self, request_id, error, thread_ts=None):
        self.errors.append(error)


class _FakePublisher:
    def __init__(self):
        self.published = []

    def publish(self, topic, payload):
        self.published.append((topic, payload))


def _msg():
    return PubSubMessage(
        topic="economic.data.update.request",
        payload={"source": "pipeline"},
        request_id="r-1",
        source="pipeline",
    )


def _run(result):
    notifier, publisher = _FakeNotifier(), _FakePublisher()
    handler = EconomicDataHandler(_FakeService(result), notifier, publisher)
    return handler, notifier, publisher


FAILED_RESULT = {
    "success": False,
    "error": "수집한 2일치를 한 건도 저장하지 못했습니다",
    "start_date": "2026-08-13",
    "end_date": "2026-08-14",
    "dates_attempted": 2,
    "dates_saved": 0,
}


def test_save_failure_raises_non_retryable():
    """저장 전건 실패는 재시도로 안 풀리므로 NonRetryableError 여야 한다."""
    handler, _, _ = _run(FAILED_RESULT)
    with pytest.raises(NonRetryableError):
        handler.handle(_msg())


def test_save_failure_does_not_report_success():
    """실패인데 성공 알림을 보내면 사고를 놓친다."""
    handler, notifier, publisher = _run(FAILED_RESULT)
    with pytest.raises(NonRetryableError):
        handler.handle(_msg())

    assert notifier.successes == [], "실패인데 성공 알림이 나갔다"
    assert notifier.errors, "실패 알림이 없다"
    topics = [t for t, _ in publisher.published]
    assert "ECONOMIC_DATA_UPDATED" not in topics
    assert "ECONOMIC_DATA_UPDATE_FAILED" in topics


def test_save_failure_does_not_chain_pipeline():
    """저장 실패 시 다음 분석 단계를 트리거하면 빈 데이터로 파이프라인이 돈다."""
    handler, _, publisher = _run(FAILED_RESULT)
    with pytest.raises(NonRetryableError):
        handler.handle(_msg())

    topics = [t for t, _ in publisher.published]
    assert "TRIGGER_TECHNICAL_ANALYSIS" not in topics
    assert "TRIGGER_SENTIMENT_ANALYSIS" not in topics


def test_successful_save_still_chains():
    """정상 저장은 기존대로 성공 알림 + 체이닝 되어야 한다(회귀 방지)."""
    handler, notifier, publisher = _run({
        "success": True,
        "start_date": "2026-08-19",
        "end_date": "2026-08-19",
        "fred_collected": 3,
        "yahoo_collected": 24,
        "dates_attempted": 1,
        "dates_saved": 1,
        "partial_failure": False,
    })
    handler.handle(_msg())

    topics = [t for t, _ in publisher.published]
    assert notifier.successes, "성공 알림이 없다"
    assert "ECONOMIC_DATA_UPDATED" in topics
    assert "TRIGGER_TECHNICAL_ANALYSIS" in topics
