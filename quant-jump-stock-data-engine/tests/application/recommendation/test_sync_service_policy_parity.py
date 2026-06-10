"""sync_service composite 가 ScoringPolicy.compose_components 와 100% 일치."""
from decimal import Decimal
from domain.recommendation.scoring_policy import ScoringPolicy


def test_compose_parity_with_adr0006_formula():
    """ADR 0006 sync_service:
       composite = (0.3*(ai/10) + 0.5*(tech/3.5) + 0.2*(sent/10)) * 100
       (모든 축 present, 0~100 스케일).
    ScoringPolicy 가 동일한 결과를 내야 함."""
    p = ScoringPolicy.load_default()
    cases = [
        # (ai, tech, sent, expected_composite)
        (Decimal("10"), Decimal("3.5"), Decimal("5"), Decimal("90.00")),
        (Decimal("0"),  Decimal("3.5"), Decimal("0"), Decimal("50.00")),
        (Decimal("5"),  Decimal("0"),   Decimal("3"), Decimal("21.00")),
        (Decimal("10"), Decimal("3.5"), Decimal("10"), Decimal("100.00")),
    ]
    for ai, tech, sent, expected in cases:
        s = p.compose_components(
            ai_score=ai, tech_score=tech, sentiment_score=sent,
            has_ai=True, has_tech=True, has_sentiment=True,
            tech_signal_count=3,
        )
        assert s.composite_score == expected, f"({ai}, {tech}, {sent}) → {s.composite_score} != {expected}"
