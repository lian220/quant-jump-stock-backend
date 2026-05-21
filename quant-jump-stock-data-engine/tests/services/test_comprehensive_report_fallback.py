"""comprehensive_report 의 effective_prediction_date 노출 검증 (PR 2)."""
from unittest.mock import MagicMock, patch

import pytest


@pytest.fixture
def mock_db():
    """daily_stock_data 있고 analysis_results 없는 mock — fallback 발동."""
    db = MagicMock()
    # daily_stock_data: target_date 존재
    db.daily_stock_data.find_one.return_value = {
        "date": "2026-05-19",
        "stocks": {
            "LLY": {"close_price": 1000.0},
        }
    }
    # stock_analysis_results: target_date 없음 (fallback 발동)
    def ar_find(query, *args, **kwargs):
        date = query.get("date")
        if date == "2026-05-19":
            return []  # target_date 없음
        if isinstance(date, dict) and "$gte" in date:
            return []
        if date == "2026-05-17":
            return [{
                "ticker": "LLY", "date": "2026-05-17",
                "predictions": {"predicted_future_price": 1100, "rise_probability": 10.0}
            }]
        return []
    db.stock_analysis_results.find.side_effect = ar_find
    # find_one 으로 fallback 찾기
    db.stock_analysis_results.find_one.return_value = {
        "ticker": "LLY", "date": "2026-05-17",
        "predictions": {"predicted_future_price": 1100, "rise_probability": 10.0}
    }
    # stock_predictions: 빈
    db.stock_predictions.find.return_value = []
    return db


def test_fallback_returns_effective_date(mock_db):
    """target_date 없으면 fallback 날짜를 두 번째 반환값에."""
    from services.comprehensive_report import ComprehensiveReportService
    with patch("services.comprehensive_report.MongoDB") as mock_mongo:
        mock_mongo.get_db.return_value = mock_db
        with patch("services.comprehensive_report._query_stock_predictions_by_date", return_value=[]):
            service = ComprehensiveReportService()
            predictions, effective_date = service.load_ai_predictions("2026-05-19")
    assert effective_date == "2026-05-17"
    assert "LLY" in predictions


def test_no_fallback_returns_none(mock_db):
    """target_date 있으면 effective_date 는 None."""
    mock_db.stock_analysis_results.find.side_effect = lambda q, *a, **kw: (
        [{"ticker": "LLY", "date": "2026-05-19",
          "predictions": {"predicted_future_price": 1100, "rise_probability": 10.0}}]
        if q.get("date") == "2026-05-19" else []
    )
    from services.comprehensive_report import ComprehensiveReportService
    with patch("services.comprehensive_report.MongoDB") as mock_mongo:
        mock_mongo.get_db.return_value = mock_db
        with patch("services.comprehensive_report._query_stock_predictions_by_date", return_value=[]):
            service = ComprehensiveReportService()
            predictions, effective_date = service.load_ai_predictions("2026-05-19")
    assert effective_date is None
    assert "LLY" in predictions
