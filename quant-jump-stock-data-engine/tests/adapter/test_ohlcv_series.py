from adapter.output.mongodb.analysis_repository import MongoPriceRepository


class _FakeCollection:
    def __init__(self, docs):
        self._docs = docs

    def find(self, query, projection=None):
        lo = query["date"]["$gte"]
        hi = query["date"]["$lte"]
        rows = [d for d in self._docs if lo <= d["date"] <= hi]
        return _FakeCursor(rows)


class _FakeCursor:
    def __init__(self, rows):
        self._rows = rows

    def sort(self, field, direction=1):
        assert field == "date" and direction == 1, f"Unexpected sort: {field}, {direction}"
        self._rows = sorted(self._rows, key=lambda d: d[field])
        return self

    def __iter__(self):
        return iter(self._rows)


def _repo_with(docs):
    repo = MongoPriceRepository.__new__(MongoPriceRepository)
    repo.collection = _FakeCollection(docs)
    return repo


def test_get_ohlcv_series_returns_sorted_ohlcv_for_ticker():
    docs = [
        {"date": "2026-01-02", "stocks": {"AAPL": {"open": 10, "high": 12, "low": 9, "close": 11, "volume": 100}}},
        {"date": "2026-01-01", "stocks": {"AAPL": {"open": 9, "high": 11, "low": 8, "close": 10, "volume": 90},
                                          "MSFT": {"open": 5, "high": 6, "low": 4, "close": 5, "volume": 50}}},
    ]
    repo = _repo_with(docs)

    rows = repo.get_ohlcv_series("AAPL", "2026-01-01", "2026-01-02")

    assert [r["date"] for r in rows] == ["2026-01-01", "2026-01-02"]
    assert rows[0] == {"date": "2026-01-01", "open": 9.0, "high": 11.0, "low": 8.0, "close": 10.0, "volume": 90}
    assert rows[1] == {"date": "2026-01-02", "open": 10.0, "high": 12.0, "low": 9.0, "close": 11.0, "volume": 100}


def test_get_ohlcv_series_skips_days_missing_ticker_or_close():
    docs = [
        {"date": "2026-01-01", "stocks": {"MSFT": {"close": 5}}},
        {"date": "2026-01-02", "stocks": {"AAPL": {"open": 1, "high": 2, "low": 0.5}}},
        {"date": "2026-01-03", "stocks": {"AAPL": {"close_price": 11, "open": 10, "high": 12, "low": 9, "volume": 7}}},
    ]
    repo = _repo_with(docs)

    rows = repo.get_ohlcv_series("AAPL", "2026-01-01", "2026-01-03")

    assert [r["date"] for r in rows] == ["2026-01-03"]
    assert rows[0]["close"] == 11.0


def test_get_ohlcv_series_volume_missing_is_none():
    docs = [{"date": "2026-02-01", "stocks": {"AAPL": {"open": 1, "high": 2, "low": 0.5, "close": 1.5}}}]
    repo = _repo_with(docs)
    rows = repo.get_ohlcv_series("AAPL", "2026-02-01", "2026-02-01")
    assert rows[0]["volume"] is None
