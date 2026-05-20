"""
analysis_repository.py 의 stock_recommendations.date 컬럼 형식 회귀 테스트.

배경 (2026-05-20 incident):
  PR #103 migration 이 기존 string date 60K건 → ISODate 변환했지만, writer 코드는
  string 그대로 저장 중이었음 → 새 데이터 35건 (5/18) 이 string 으로 들어가 sync_service
  ISODate query 가 0건으로 보고 → freshness probe 알람.

본 테스트는 writer 가 항상 datetime 객체를 저장하는지 검증.
"""
from datetime import datetime

import pytest

from application.analysis.ports import TechnicalResult
from adapter.output.mongodb.analysis_repository import MongoAnalysisResultRepository


def _make_result(date_value):
    return TechnicalResult(
        ticker="AAPL",
        stock_name="Apple",
        date=date_value,
        sma20=100.0,
        sma50=95.0,
        rsi=45.0,
        macd=1.2,
        signal=1.0,
        golden_cross=True,
        macd_buy_signal=True,
        is_recommended=True,
    )


def test_to_document_with_datetime_keeps_datetime():
    repo = MongoAnalysisResultRepository.__new__(MongoAnalysisResultRepository)
    expected = datetime(2026, 5, 18)
    doc = repo._to_document(_make_result(expected))

    assert isinstance(doc["date"], datetime), \
        "stock_recommendations.date 는 ISODate 로 저장되어야 한다 (Mongo BSON datetime)"
    assert doc["date"] == expected


def test_to_document_with_legacy_string_is_converted_defensively():
    """방어선: 누군가 legacy 코드로 string 을 넣어도 ISODate 로 변환 후 저장."""
    repo = MongoAnalysisResultRepository.__new__(MongoAnalysisResultRepository)
    doc = repo._to_document(_make_result("2026-05-18"))

    assert isinstance(doc["date"], datetime), \
        "legacy string 이 흘러와도 BSON 직저장 직전에 datetime 으로 변환되어야 한다"
    assert doc["date"] == datetime(2026, 5, 18)
