"""PR 3b: negative AI veto unit tests (cleanup PR 2026-05-22: compose_components 직접 호출)."""
from decimal import Decimal

import pytest

from domain.recommendation.scoring_policy import ScoringPolicy
from domain.recommendation.exceptions import SpecValidationError


def _compose_with_raw_rise(p: ScoringPolicy, rise_pct, sentiment_raw, tech_indicators):
    """raw input → compose_components — score_from_raw_signals 헬퍼 제거 후 prod path 동일 흐름."""
    ai_s = p.normalize_rise_pct_to_score(Decimal(str(rise_pct)) if rise_pct is not None else None)
    tech_s = p.tech_score_from_indicators(tech_indicators)
    sent_s = p.sentiment_score_from_raw(Decimal(str(sentiment_raw)) if sentiment_raw is not None else None)
    veto: tuple = ()
    if rise_pct is not None and Decimal(str(rise_pct)) < Decimal("0") and p.is_negative_veto_enabled():
        veto = ("ai_negative",)
    return p.compose_components(
        ai_score=ai_s, tech_score=tech_s, sentiment_score=sent_s,
        has_ai=rise_pct is not None,
        has_tech=bool(tech_indicators),
        has_sentiment=sentiment_raw is not None,
        tech_signal_count=p.count_tech_signals(tech_indicators),
        veto_reasons=veto,
    )


def test_negative_veto_enabled_for_v1_1_0_spec():
    """PR 3b spec → is_negative_veto_enabled True (negative veto 는 1.1.0부터 유지)."""
    p = ScoringPolicy.load_default()
    assert p.is_negative_veto_enabled() is True
    # PR 5 머지 후 spec 1.2.0 (VIX gate). negative veto 자체는 1.1.0 부터 변경 없음.
    assert p.formula_version >= "1.1.0"


def test_negative_pct_vetoes():
    """rise_pct < 0 → composite=0, grade=D, label=NONE, veto_reasons=('ai_negative',)."""
    p = ScoringPolicy.load_default()
    s = _compose_with_raw_rise(p, -10, 0.5, {"golden_cross": True, "rsi": 30, "macd_buy_signal": True})
    assert s.composite_score == Decimal("0.00")
    assert s.grade == "D"
    assert s.recommendation_label == "NONE"
    assert s.veto_reasons == ("ai_negative",)


def test_zero_pct_no_veto():
    """rise_pct == 0 → veto 미발동 (산식상 normalized=0.5 = 보더)."""
    p = ScoringPolicy.load_default()
    s = _compose_with_raw_rise(p, 0, 0.3, {"golden_cross": True, "rsi": 50, "macd_buy_signal": True})
    assert s.veto_reasons == ()


def test_positive_pct_no_veto():
    """rise_pct > 0 → veto 미발동, 정상 composite."""
    p = ScoringPolicy.load_default()
    s = _compose_with_raw_rise(p, 15, 0.5, {"golden_cross": True, "rsi": 30, "macd_buy_signal": True})
    assert s.veto_reasons == ()
    assert s.composite_score > Decimal("0")


def test_compose_components_with_explicit_veto_reasons():
    """sync_service 케이스 — caller 가 veto_reasons 명시 전달."""
    p = ScoringPolicy.load_default()
    s = p.compose_components(
        ai_score=Decimal("10"),
        tech_score=Decimal("3.5"),
        sentiment_score=Decimal("10"),
        has_ai=True, has_tech=True, has_sentiment=True,
        tech_signal_count=3,
        veto_reasons=("ai_negative",),
    )
    # veto 발동 → composite 강제 0 (원래는 7.4 였을 것)
    assert s.composite_score == Decimal("0.00")
    assert s.grade == "D"
    assert s.recommendation_label == "NONE"
    assert s.veto_reasons == ("ai_negative",)


def test_compose_components_veto_ignored_when_policy_zero(tmp_path, make_spec):
    """negative_policy='zero' spec 으로 로드 시 veto_reasons 전달해도 무시 (rollback safety)."""
    spec_path = make_spec(tmp_path, axes={
        "ai": {"weight": 0.3, "max": 10.0, "cap_strategy": "fixed_pct",
               "cap_threshold_pct": 20.0, "missing_policy": "zero",
               "negative_policy": "zero"},  # ← zero
    })
    policy = ScoringPolicy.load(str(spec_path))
    assert policy.is_negative_veto_enabled() is False
    s = policy.compose_components(
        ai_score=Decimal("10"), tech_score=Decimal("3.5"), sentiment_score=Decimal("10"),
        has_ai=True, has_tech=True, has_sentiment=True, tech_signal_count=3,
        veto_reasons=("ai_negative",),
    )
    # zero 모드 → veto_reasons 무시. 정상 7.40 으로 산출.
    assert s.composite_score == Decimal("7.40")
    assert s.veto_reasons == ()  # zero 모드는 항상 빈 튜플 반환


def test_spec_invariant_rejects_invalid_negative_policy(tmp_path, make_spec):
    """negative_policy 가 zero/veto 가 아니면 SpecValidationError."""
    bad = make_spec(tmp_path, axes={"ai": {"weight": 0.3, "max": 10.0, "cap_strategy": "fixed_pct",
                                            "cap_threshold_pct": 20.0, "missing_policy": "zero",
                                            "negative_policy": "ignore"}})  # invalid
    with pytest.raises(SpecValidationError, match="negative_policy"):
        ScoringPolicy.load(str(bad))
