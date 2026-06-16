"""EconomicDataRepository.upsert_daily_data per-key 병합 회귀 테스트.

배경 (2026-06-15 사고):
  fred/yfinance/stocks 를 통째 $set 하면, 같은 거래일을 재수집하는 후속 런이
  부분 실패(yfinance throttle 로 42→18 종목)했을 때 기존 완전 stocks 맵을 통째
  덮어써 데이터가 영구 손실됐다. 하위 키만 dot-path 로 개별 set 해야 보존된다.

mongo 없이 update_one 호출 인자만 캡처하여 $set 구조를 검증.
"""
from features.economic_data.repository import EconomicDataRepository


class _FakeResult:
    acknowledged = True


class _FakeCollection:
    def __init__(self):
        self.calls = []

    def update_one(self, filt, update, upsert=False):
        self.calls.append({"filter": filt, "update": update, "upsert": upsert})
        return _FakeResult()


class _FakeDB:
    def __init__(self, collection):
        self._collection = collection

    def __getitem__(self, _name):
        return self._collection


def _make_repo():
    # __init__ 은 MongoDB.get_db() 를 호출하므로 우회 생성 후 fake db 주입
    repo = EconomicDataRepository.__new__(EconomicDataRepository)
    coll = _FakeCollection()
    repo.db = _FakeDB(coll)
    return repo, coll


def test_stocks_merged_per_ticker_not_wholesale():
    """stocks 는 'stocks' 통째가 아니라 'stocks.<ticker>' dot-path 로 set 되어야 한다."""
    repo, coll = _make_repo()

    ok = repo.upsert_daily_data(
        "2026-06-15",
        {
            "stocks": {"AAPL": {"close_price": 1.0}, "NVDA": {"close_price": 2.0}},
            "fred_indicators": {"FEDFUNDS": {"value": 4.33}},
            "yfinance_indicators": {"^GSPC": {"close_price": 5000.0}},
        },
    )

    assert ok is True
    assert len(coll.calls) == 1
    call = coll.calls[0]
    assert call["filter"] == {"date": "2026-06-15"}
    assert call["upsert"] is True

    s = call["update"]["$set"]
    # 통째 교체 금지 — clobber 의 직접 원인
    assert "stocks" not in s
    assert "fred_indicators" not in s
    assert "yfinance_indicators" not in s
    # per-key dot-path
    assert s["stocks.AAPL"] == {"close_price": 1.0}
    assert s["stocks.NVDA"] == {"close_price": 2.0}
    assert s["fred_indicators.FEDFUNDS"] == {"value": 4.33}
    assert s["yfinance_indicators.^GSPC"] == {"close_price": 5000.0}
    assert "updated_at" in s


def test_partial_run_does_not_clobber_missing_tickers():
    """부분 수집(18종목)이 누락 24종목의 stocks 키를 건드리지 않아야 한다."""
    repo, coll = _make_repo()
    repo.upsert_daily_data("2026-06-15", {"stocks": {"AAPL": {"close_price": 1.0}}})

    s = coll.calls[0]["update"]["$set"]
    # 오직 AAPL 만 set, 'stocks' 통째 키는 없음 → 기존 다른 티커 보존
    assert s["stocks.AAPL"] == {"close_price": 1.0}
    assert "stocks" not in s
    assert [k for k in s if k.startswith("stocks.")] == ["stocks.AAPL"]


def test_dotted_or_dollar_key_falls_back_to_wholesale():
    """티커에 dot/$ 가 있으면 dot-path 가 위험하므로 그 맵만 통째 set (드문 방어 경로)."""
    repo, coll = _make_repo()
    repo.upsert_daily_data("2026-06-15", {"stocks": {"BRK.B": {"close_price": 1.0}}})

    s = coll.calls[0]["update"]["$set"]
    assert s["stocks"] == {"BRK.B": {"close_price": 1.0}}
    assert "stocks.BRK.B" not in s


def test_non_merge_top_level_keys_set_directly():
    """date 등 병합대상이 아닌 최상위 키는 그대로 set."""
    repo, coll = _make_repo()
    repo.upsert_daily_data("2026-06-15", {"date": "2026-06-15", "stocks": {"AAPL": {"close_price": 1.0}}})

    s = coll.calls[0]["update"]["$set"]
    assert s["date"] == "2026-06-15"
