"""sync_service composite 가 ScoringPolicy.compose_components 와 100% 일치."""
from decimal import Decimal
from domain.recommendation.scoring_policy import ScoringPolicy


def test_compose_parity_with_legacy_sync_formula():
    """현행 sync_service:
       composite = 0.3*ai_score + 0.4*tech_score + 0.3*sentiment_score
       (AI 10, Tech 3.5, Sent 10 scale)
    ScoringPolicy 가 동일한 결과를 내야 함."""
    p = ScoringPolicy.load_default()
    cases = [
        # (ai, tech, sent, expected_composite)
        (Decimal("10"), Decimal("3.5"), Decimal("5"), Decimal("5.90")),
        (Decimal("0"),  Decimal("3.5"), Decimal("0"), Decimal("1.40")),
        (Decimal("5"),  Decimal("0"),   Decimal("3"), Decimal("2.40")),
        (Decimal("10"), Decimal("3.5"), Decimal("10"), Decimal("7.40")),
    ]
    for ai, tech, sent, expected in cases:
        s = p.compose_components(
            ai_score=ai, tech_score=tech, sentiment_score=sent,
            has_ai=True, has_tech=True, has_sentiment=True,
            tech_signal_count=3,
        )
        assert s.composite_score == expected, f"({ai}, {tech}, {sent}) → {s.composite_score} != {expected}"
