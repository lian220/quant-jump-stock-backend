"""Slack 분모가 ScoringPolicy.composite_max 에서. 현행 7.4 그대로 표시."""
from decimal import Decimal

from services.slack_notifier import _format_score_for_slack


def test_format_uses_score_composite_max(make_score):
    s = make_score(composite_score=Decimal("5.90"), composite_max=Decimal("7.40"), confidence=Decimal("0.797"))
    out = _format_score_for_slack(s)
    assert "5.90/7.40" in out  # 분모 7.40
    assert "80%" in out         # 0.797 → 80%


def test_format_falls_back_when_max_differs(make_score):
    """spec 변경 시(예: composite_max=9.0) 분모도 그에 맞춰 동적 표시."""
    s = make_score(composite_score=Decimal("6.30"), composite_max=Decimal("9.00"), confidence=Decimal("0.700"))
    out = _format_score_for_slack(s)
    assert "6.30/9.00" in out
    assert "70%" in out
