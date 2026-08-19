"""ensure_indexes 가 드롭된 idx_date 를 되살리지 않는지 검증.

배경 (2026-08-13 Atlas 용량 초과 사고 후속):
  daily_stock_data.idx_date 는 date_unique 와 완전 중복이라 8/18 사고 대응에서
  드롭했다. 그런데 ensure_indexes 가 부팅마다 idx_date 를 재생성하고, data-engine 은
  scale-to-zero 라 콜드스타트가 잦다 — 코드를 지우지 않으면 드롭이 계속 원복된다.
  (MongoDB_용량정리_점검.md §2 1단계의 선행 조건)
"""
from core.database import MongoDB


class _FakeCollection:
    def __init__(self):
        self.created = []

    def create_index(self, keys, **kwargs):
        self.created.append({"keys": keys, **kwargs})
        return kwargs.get("name", "")


class _FakeDB(dict):
    def __missing__(self, name):
        coll = _FakeCollection()
        self[name] = coll
        return coll


def test_idx_date_not_recreated():
    """daily_stock_data 에 idx_date 를 다시 만들면 안 된다 (date_unique 와 중복)."""
    db = _FakeDB()
    MongoDB.ensure_indexes(db)

    names = [c.get("name") for c in db["daily_stock_data"].created]
    assert "idx_date" not in names, "드롭한 중복 인덱스를 부팅마다 되살리고 있다"


def test_ticker_date_unique_still_ensured():
    """stock_recommendations 의 unique 인덱스 보장은 유지되어야 한다 (회귀 방지)."""
    db = _FakeDB()
    MongoDB.ensure_indexes(db)

    recs = db["stock_recommendations"].created
    assert any(
        c.get("name") == "ticker_date_unique" and c.get("unique") is True
        for c in recs
    )
