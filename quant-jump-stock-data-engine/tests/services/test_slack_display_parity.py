"""Slack 분모가 ScoringPolicy.composite_max 에서. 현행 7.4 그대로 표시."""
from decimal import Decimal

from domain.recommendation.score import Score
from services.slack_notifier import _format_score_for_slack


def _score(composite: str, max_: str, confidence: str) -> Score:
    return Score(
        ai_score=Decimal("0"),
        tech_score=Decimal("0"),
        sentiment_score=Decimal("0"),
        composite_score=Decimal(composite),
        composite_max=Decimal(max_),
        confidence=Decimal(confidence),
        grade="A",
        recommendation_label="RECOMMEND",
    )


def test_format_uses_score_composite_max():
    s = _score("5.90", "7.40", "0.797")
    out = _format_score_for_slack(s)
    assert "5.90/7.40" in out  # 분모 7.40
    assert "80%" in out         # 0.797 → 80%


def test_format_falls_back_when_max_differs():
    """spec 변경 시(예: composite_max=9.0) 분모도 그에 맞춰 동적 표시."""
    s = _score("6.30", "9.00", "0.700")
    out = _format_score_for_slack(s)
    assert "6.30/9.00" in out
    assert "70%" in out
