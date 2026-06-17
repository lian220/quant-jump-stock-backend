"""_fetch_yahoo_data yfinance throttle 재시도 테스트.

배경 (2026-06-15 사고 후속): yfinance throttle 시 예외 없이 빈 df 를 반환 → 종목 누락.
빈 응답/예외를 지수 백오프로 재시도하여 회복률을 높인다.
"""
import pandas as pd
import pytest

from features.economic_data.service import EconomicDataService


def _service():
    # __init__ 우회 (외부 의존 없이 메서드만 테스트)
    return EconomicDataService.__new__(EconomicDataService)


def _valid_df(date="2026-06-15"):
    idx = pd.to_datetime([date])
    return pd.DataFrame(
        {"Open": [1.0], "High": [1.0], "Low": [1.0], "Close": [1.0], "Volume": [10]},
        index=idx,
    )


class _FakeStock:
    """history() 가 주어진 시퀀스대로 반환(빈 df → 정상 df 등)."""

    def __init__(self, sequence):
        self._seq = list(sequence)
        self.calls = 0

    def history(self, **_kw):
        self.calls += 1
        item = self._seq[min(self.calls - 1, len(self._seq) - 1)]
        if isinstance(item, Exception):
            raise item
        return item


@pytest.fixture(autouse=True)
def _no_sleep(monkeypatch):
    monkeypatch.setattr("features.economic_data.service.time.sleep", lambda *a, **k: None)


def test_retries_empty_then_succeeds():
    """첫 호출 빈 df(throttle) → 재시도 후 정상 df 회복."""
    stock = _FakeStock([pd.DataFrame(), _valid_df()])
    df = _service()._fetch_yahoo_data("AAPL", "2026-06-15", "2026-06-15", stock_obj=stock, max_retries=2)
    assert df is not None and not df.empty
    assert stock.calls == 2  # 1회 재시도


def test_retries_exhausted_returns_none():
    """계속 빈 df → max_retries+1 회 시도 후 None."""
    stock = _FakeStock([pd.DataFrame()])
    df = _service()._fetch_yahoo_data("AAPL", "2026-06-15", "2026-06-15", stock_obj=stock, max_retries=2)
    assert df is None
    assert stock.calls == 3  # 최초 + 2 재시도


def test_exception_then_success():
    """예외 → 재시도 후 정상 회복."""
    stock = _FakeStock([RuntimeError("Too Many Requests"), _valid_df()])
    df = _service()._fetch_yahoo_data("AAPL", "2026-06-15", "2026-06-15", stock_obj=stock, max_retries=2)
    assert df is not None and not df.empty
    assert stock.calls == 2


def test_cutoff_empty_not_retried():
    """완성봉 cutoff 후 빈 것(미래 날짜만 반환)은 throttle 아님 → 재시도 안 함, None."""
    future = _valid_df("2027-01-01")  # cutoff(=최근 거래일) 이후 → 필터링되어 빈 df
    stock = _FakeStock([future, future, future])
    df = _service()._fetch_yahoo_data("AAPL", "2026-06-15", "2026-06-15", stock_obj=stock, max_retries=2)
    assert df is None
    assert stock.calls == 1  # 재시도 없음
