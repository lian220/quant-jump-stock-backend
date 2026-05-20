"""
analysis_repository.py 의 stock_recommendations.date 컬럼 형식 회귀 테스트.

배경 (2026-05-20):
  전체 MongoDB 컬렉션 (daily_stock_data / sentiment_analysis / stock_recommendations /
  stock_predictions / stock_analysis_results) 의 `date` 필드를 NYSE 거래일 string
  ("%Y-%m-%d") 으로 통일 결정. 거래일 = timezone-independent 논리 개념이므로 datetime/ISODate
  사용 시 UTC 변환으로 timezone 모호성 유발.

본 테스트는 writer 가 항상 string 을 저장하는지 검증.
"""
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


def test_to_document_with_string_keeps_string():
    """writer 가 NYSE 거래일 string 을 그대로 저장 (datetime 변환 없음)."""
    repo = MongoAnalysisResultRepository.__new__(MongoAnalysisResultRepository)
    doc = repo._to_document(_make_result("2026-05-18"))

    assert doc["date"] == "2026-05-18"
    assert isinstance(doc["date"], str), \
        "stock_recommendations.date 는 NYSE 거래일 string 으로 저장 (timezone-independent)"
