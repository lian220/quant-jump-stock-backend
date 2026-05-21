"""Property-based tests — ScoringPolicy.compose_components 구조적 불변량.

Golden CSV (test_golden.py) 가 50 케이스 frozen 동작, 이 파일이 연속 도메인 전반의
불변량 검증. Hypothesis 가 자동 shrinking 으로 실패 케이스를 최소 형태로 보고.

검증 속성:
  1. composite_within_range  — 결과가 [0, composite_max] 범위 내
  2. ai_monotonic            — ai_score 증가 시 composite 비감소 (±0.01 허용)
  3. weight_sum_invariant    — 가중치 합 == 1.0 (spec 불변량 방어 검증)
  4. tech_monotonic          — tech_score 증가 시 composite 비감소 (±0.01 허용)
  5. missing_axis_reduces_composite — has_sentiment=False 시 composite 비증가
"""
from decimal import Decimal

from hypothesis import given, settings, strategies as st

from domain.recommendation.scoring_policy import ScoringPolicy


# 모듈 레벨 캐시: 전체 테스트에서 한 번만 spec 로드
P = ScoringPolicy.load_default()


def _decimal_in_range(min_value: str, max_value: str):
    """Decimal strategy with reasonable precision (avoids subnormal silliness)."""
    return st.decimals(
        min_value=Decimal(min_value),
        max_value=Decimal(max_value),
        allow_nan=False,
        allow_infinity=False,
        places=2,  # 0.01 step — matches policy's quantize precision
    )


# ── 1. composite_within_range ──────────────────────────────────────────────────

@given(
    ai=_decimal_in_range("0", "10"),
    tech=_decimal_in_range("0", "3.5"),
    sent=_decimal_in_range("0", "10"),
)
@settings(max_examples=300, deadline=None)
def test_composite_within_range(ai, tech, sent):
    """composite_score ∈ [0, composite_max] for all valid inputs."""
    s = P.compose_components(
        ai_score=ai, tech_score=tech, sentiment_score=sent,
        has_ai=True, has_tech=True, has_sentiment=True,
        tech_signal_count=3,
    )
    assert Decimal("0") <= s.composite_score <= s.composite_max


# ── 2. ai_monotonic ────────────────────────────────────────────────────────────

@given(
    ai_lo=_decimal_in_range("0", "9"),
    delta=_decimal_in_range("0", "1"),
    tech=_decimal_in_range("0", "3.5"),
    sent=_decimal_in_range("0", "10"),
)
@settings(max_examples=200, deadline=None)
def test_ai_monotonic(ai_lo, delta, tech, sent):
    """increasing ai_score (holding tech/sent constant) does not decrease composite."""
    common = dict(
        tech_score=tech, sentiment_score=sent,
        has_ai=True, has_tech=True, has_sentiment=True, tech_signal_count=3,
    )
    s_lo = P.compose_components(ai_score=ai_lo, **common)
    s_hi = P.compose_components(ai_score=ai_lo + delta, **common)
    # tolerance for quantize edge (ROUND_HALF_UP on 0.01 granularity)
    assert s_hi.composite_score >= s_lo.composite_score - Decimal("0.01")


# ── 3. weight_sum_invariant ────────────────────────────────────────────────────

def test_weight_sum_invariant():
    """spec axes weights must sum to 1.0 (defensive — also checked at policy load)."""
    s = sum(Decimal(str(P.axes[k]["weight"])) for k in P.axes)
    assert abs(s - Decimal("1.0")) < Decimal("0.001")


# ── 4. tech_monotonic (bonus) ──────────────────────────────────────────────────

@given(
    ai=_decimal_in_range("0", "10"),
    tech_lo=_decimal_in_range("0", "2.5"),
    delta=_decimal_in_range("0", "1.0"),
    sent=_decimal_in_range("0", "10"),
)
@settings(max_examples=200, deadline=None)
def test_tech_monotonic(ai, tech_lo, delta, sent):
    """increasing tech_score (holding ai/sent constant) does not decrease composite."""
    common = dict(
        ai_score=ai, sentiment_score=sent,
        has_ai=True, has_tech=True, has_sentiment=True, tech_signal_count=3,
    )
    s_lo = P.compose_components(tech_score=tech_lo, **common)
    s_hi = P.compose_components(tech_score=tech_lo + delta, **common)
    assert s_hi.composite_score >= s_lo.composite_score - Decimal("0.01")


# ── 5. missing_axis_reduces_composite (bonus) ──────────────────────────────────

@given(
    ai=_decimal_in_range("0", "10"),
    tech=_decimal_in_range("0", "3.5"),
    sent=_decimal_in_range("0", "10"),
)
@settings(max_examples=150, deadline=None)
def test_missing_axis_reduces_or_equal(ai, tech, sent):
    """has_sentiment=False (sentiment forced to 0) → composite ≤ has_sentiment=True case.

    Specifically: with sentiment value provided but flagged missing, effective_sentiment=0,
    so composite drops by 0.3 * sent.
    """
    common = dict(
        ai_score=ai, tech_score=tech, sentiment_score=sent,
        has_ai=True, has_tech=True, tech_signal_count=3,
    )
    s_present = P.compose_components(has_sentiment=True, **common)
    s_missing = P.compose_components(has_sentiment=False, **common)
    assert s_missing.composite_score <= s_present.composite_score + Decimal("0.01")
