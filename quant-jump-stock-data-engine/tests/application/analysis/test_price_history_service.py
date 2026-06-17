from datetime import datetime, timedelta

from application.analysis.price_history_service import (
    PriceHistoryService,
    period_to_dates,
)


def test_period_to_dates_maps_tokens():
    end = "2026-06-17"
    assert period_to_dates("1m", end) == ("2026-05-18", "2026-06-17")
    assert period_to_dates("1y", end) == ("2025-06-17", "2026-06-17")


def test_period_to_dates_rejects_unknown():
    try:
        period_to_dates("2w", "2026-06-17")
        assert False, "expected ValueError"
    except ValueError:
        pass


class _FakeRepo:
    """200 거래일치 단조 증가 종가."""
    def __init__(self):
        base = datetime(2025, 9, 1)
        self.rows = [{
            "date": (base + timedelta(days=i)).strftime("%Y-%m-%d"),
            "open": float(100 + i), "high": float(101 + i),
            "low": float(99 + i), "close": float(100 + i), "volume": 1000 + i,
        } for i in range(200)]

    def get_ohlcv_series(self, ticker, start_date, end_date):
        return [r for r in self.rows if start_date <= r["date"] <= end_date]


def test_build_returns_window_with_indicator_keys():
    svc = PriceHistoryService(_FakeRepo())

    result = svc.build("AAPL", "1m", today="2026-03-20")

    assert result["ticker"] == "AAPL"
    assert result["period"] == "1m"
    assert len(result["candles"]) > 0
    c = result["candles"][-1]
    assert set(c.keys()) == {"date", "open", "high", "low", "close", "volume",
                             "ma20", "ma60", "rsi", "macd", "signal", "histogram"}
    assert c["ma20"] is not None
    assert c["rsi"] is not None
    assert c["macd"] is not None


def test_build_empty_when_no_data():
    class _Empty:
        def get_ohlcv_series(self, *_a):
            return []
    svc = PriceHistoryService(_Empty())
    result = svc.build("ZZZ", "1m", today="2026-03-20")
    assert result["candles"] == []
