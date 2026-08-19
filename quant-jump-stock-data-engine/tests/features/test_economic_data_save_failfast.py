"""collect_economic_data 저장 실패 fail-fast 회귀 테스트.

배경 (2026-08-13 MongoDB Atlas M0 용량 초과 사고):
  Atlas 쓰기가 전면 차단된 5일 동안 수집은 매번 "성공"했다. upsert 가 전부
  실패해 dates_saved == 0 이었는데도 collect_economic_data 가 success: True 를
  반환해, 파이프라인이 "경제 데이터 수집 완료 ✅" 로 진행하고 다음 단계를
  체이닝했다. 그래서 5일간 아무도 알아채지 못했다.

  수집할 데이터가 있었는데 한 건도 저장되지 않았다면 그것은 실패다.
"""
from collections import defaultdict

from features.economic_data.service import EconomicDataService


class _FakeRepo:
    """upsert_daily_data 결과를 테스트가 지정할 수 있는 가짜 저장소."""

    def __init__(self, results):
        # results: 호출 순서대로 반환할 bool 리스트
        self._results = list(results)
        self.calls = []

    def upsert_daily_data(self, date_str, data):
        self.calls.append(date_str)
        return self._results.pop(0) if self._results else False


def _make_service(repo, collected_dates):
    """수집 단계를 stub 한 서비스. collected_dates 만큼 daily_data 를 채운다."""
    svc = EconomicDataService.__new__(EconomicDataService)
    svc.repository = repo

    svc._resolve_date_range = lambda s, e: ("2026-08-13", "2026-08-13")
    svc._load_fred_indicators = lambda: {}
    svc._load_yfinance_indicators = lambda: {}

    def _fill(*args):
        daily_data = args[-1]
        for d in collected_dates:
            daily_data[d]["stocks"]["AAPL"] = {"close": 1.0}
        return len(collected_dates)

    svc._collect_fred_data_grouped = _fill
    svc._collect_yahoo_data_grouped = lambda *a: 0
    svc._collect_individual_stocks = lambda *a: len(collected_dates)
    return svc


def test_all_writes_failed_returns_failure():
    """수집 데이터가 있는데 저장이 전건 실패하면 success: False 여야 한다."""
    repo = _FakeRepo(results=[False, False])
    svc = _make_service(repo, ["2026-08-13", "2026-08-14"])

    result = svc.collect_economic_data()

    assert result["dates_saved"] == 0
    assert result["success"] is False, "저장 0건인데 성공으로 보고하면 사고를 놓친다"


def test_partial_save_is_flagged():
    """일부 날짜만 저장되면 부분 실패로 표시되어야 한다."""
    repo = _FakeRepo(results=[True, False])
    svc = _make_service(repo, ["2026-08-13", "2026-08-14"])

    result = svc.collect_economic_data()

    assert result["dates_saved"] == 1
    assert result["dates_attempted"] == 2
    assert result["partial_failure"] is True


def test_nothing_collected_is_not_a_failure():
    """수집할 데이터 자체가 없으면(휴장일 등) 실패가 아니다."""
    repo = _FakeRepo(results=[])
    svc = _make_service(repo, [])

    result = svc.collect_economic_data()

    assert result["dates_saved"] == 0
    assert result["success"] is True
    assert result.get("partial_failure", False) is False
