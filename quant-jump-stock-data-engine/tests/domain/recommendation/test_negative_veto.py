"""PR 3b: negative AI veto unit tests."""
from decimal import Decimal

import pytest

from domain.recommendation.scoring_policy import ScoringPolicy
from domain.recommendation.exceptions import SpecValidationError


def test_negative_veto_enabled_for_v1_1_0_spec():
    """PR 3b spec → is_negative_veto_enabled True."""
    p = ScoringPolicy.load_default()
    assert p.is_negative_veto_enabled() is True
    assert p.formula_version == "1.1.0"


def test_score_from_raw_signals_negative_pct_vetoes():
    """rise_pct < 0 → composite=0, grade=D, label=NONE, veto_reasons=('ai_negative',)."""
    p = ScoringPolicy.load_default()
    s = p.score_from_raw_signals(
        rise_pct=Decimal("-10"),
        sentiment_raw=Decimal("0.5"),
        tech_indicators={"golden_cross": True, "rsi": 30, "macd_buy_signal": True},
    )
    assert s.composite_score == Decimal("0.00")
    assert s.grade == "D"
    assert s.recommendation_label == "NONE"
    assert s.veto_reasons == ("ai_negative",)


def test_score_from_raw_signals_zero_pct_no_veto():
    """rise_pct == 0 → veto 미발동 (산식상 normalized=0.5 = 보더)."""
    p = ScoringPolicy.load_default()
    s = p.score_from_raw_signals(
        rise_pct=Decimal("0"),
        sentiment_raw=Decimal("0.3"),
        tech_indicators={"golden_cross": True, "rsi": 50, "macd_buy_signal": True},
    )
    assert s.veto_reasons == ()


def test_score_from_raw_signals_positive_pct_no_veto():
    """rise_pct > 0 → veto 미발동, 정상 composite."""
    p = ScoringPolicy.load_default()
    s = p.score_from_raw_signals(
        rise_pct=Decimal("15"),
        sentiment_raw=Decimal("0.5"),
        tech_indicators={"golden_cross": True, "rsi": 30, "macd_buy_signal": True},
    )
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
