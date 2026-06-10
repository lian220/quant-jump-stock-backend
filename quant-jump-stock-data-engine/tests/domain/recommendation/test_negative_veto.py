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
    if rise_pct is not None and p.should_veto_rise_pct(Decimal(str(rise_pct))):
        veto = ("ai_negative",)
    return p.compose_components(
        ai_score=ai_s, tech_score=tech_s, sentiment_score=sent_s,
        has_ai=rise_pct is not None,
        has_tech=bool(tech_indicators),
        has_sentiment=sentiment_raw is not None,
        tech_signal_count=p.count_tech_signals(tech_indicators),
        veto_reasons=veto,
    )


def test_negative_veto_disabled_by_default_adr0006():
    """ADR 0006 §2.5: 기본 1차 구현은 veto 비활성 (negative_policy=zero)."""
    p = ScoringPolicy.load_default()
    assert p.is_negative_veto_enabled() is False
    assert p.formula_version == "2.0.0"


def test_negative_pct_no_veto_continuous_low_score():
    """ADR 0006 §2.5: veto 비활성 → 하락 예측도 composite=0 강제 아님, 연속 저점수.

    rise -10% → ai normalized 0.25 → ai_score 2.5. veto_reasons 빈 튜플.
    """
    p = ScoringPolicy.load_default()
    s = _compose_with_raw_rise(p, -10, 0.0, {"golden_cross": True, "rsi": 30, "macd_buy_signal": True})
    # veto 비활성 → composite > 0 (강제 0 아님)
    assert s.composite_score > Decimal("0")
    assert s.veto_reasons == ()
    # ai_score 가 연속 저점수 (0 아님)
    assert s.ai_score == Decimal("2.50")


def test_strong_negative_no_veto_when_disabled():
    """강한 하락(-15%)도 veto 비활성 시 차단 안 됨 — tech/sent 가 composite 견인."""
    p = ScoringPolicy.load_default()
    s = _compose_with_raw_rise(p, -15, 0.35, {"golden_cross": True, "rsi": 30, "macd_buy_signal": True})
    assert s.veto_reasons == ()
    assert s.composite_score > Decimal("0")


def test_positive_pct_no_veto():
    """rise_pct > 0 → veto 미발동, 정상 composite."""
    p = ScoringPolicy.load_default()
    s = _compose_with_raw_rise(p, 15, 0.3, {"golden_cross": True, "rsi": 30, "macd_buy_signal": True})
    assert s.veto_reasons == ()
    assert s.composite_score > Decimal("0")


def test_veto_active_when_spec_override_veto(tmp_path, make_spec):
    """spec override negative_policy='veto' → veto 활성. 강제 composite=0/grade D/label NONE 유지."""
    spec_path = make_spec(tmp_path, axes={
        "ai": {"weight": 0.3, "max": 10.0, "cap_strategy": "fixed_pct",
               "cap_threshold_pct": 20.0, "missing_policy": "redistribute",
               "negative_policy": "veto"},  # ← veto override
    })
    p = ScoringPolicy.load(str(spec_path))
    assert p.is_negative_veto_enabled() is True
    s = p.compose_components(
        ai_score=Decimal("10"), tech_score=Decimal("3.5"), sentiment_score=Decimal("10"),
        has_ai=True, has_tech=True, has_sentiment=True, tech_signal_count=3,
        veto_reasons=("ai_negative",),
    )
    # veto 발동 → composite 강제 0 (원래는 100 이었을 것)
    assert s.composite_score == Decimal("0.00")
    assert s.grade == "D"
    assert s.recommendation_label == "NONE"
    assert s.veto_reasons == ("ai_negative",)
    assert s.is_recommended is False


def test_compose_components_veto_ignored_when_policy_zero():
    """기본 spec(negative_policy='zero') 에서 veto_reasons 전달해도 무시 (rollback safety)."""
    policy = ScoringPolicy.load_default()
    assert policy.is_negative_veto_enabled() is False
    s = policy.compose_components(
        ai_score=Decimal("10"), tech_score=Decimal("3.5"), sentiment_score=Decimal("10"),
        has_ai=True, has_tech=True, has_sentiment=True, tech_signal_count=3,
        veto_reasons=("ai_negative",),
    )
    # zero 모드 → veto_reasons 무시. 정상 100 으로 산출.
    assert s.composite_score == Decimal("100.00")
    assert s.veto_reasons == ()  # zero 모드는 항상 빈 튜플 반환


# ── ADR 0006 §2.5 veto 확정 조건: 강한 하락(veto_threshold_pct)만 차단 ──────────

def _veto_spec(tmp_path, make_spec, **ai_overrides):
    """negative_policy=veto + veto_threshold_pct 조합 spec 생성."""
    ai = {"weight": 0.3, "max": 10.0, "cap_strategy": "fixed_pct",
          "cap_threshold_pct": 20.0, "missing_policy": "redistribute",
          "negative_policy": "veto", "veto_threshold_pct": -10.0}
    ai.update(ai_overrides)
    return make_spec(tmp_path, axes={"ai": ai})


def test_veto_threshold_pct_loaded_from_spec():
    """prod spec 은 veto 후보 임계 -10% 를 보존한다 (ADR 0006 §2.5)."""
    p = ScoringPolicy.load_default()
    assert p.veto_threshold_pct == Decimal("-10.0")


def test_should_veto_rise_pct_only_strong_negative(tmp_path, make_spec):
    """veto 활성 시 임계 미만 강한 하락만 veto. -5% 같은 약한 하락은 통과."""
    p = ScoringPolicy.load(str(_veto_spec(tmp_path, make_spec)))
    assert p.should_veto_rise_pct(Decimal("-12")) is True
    assert p.should_veto_rise_pct(Decimal("-10")) is False  # 경계는 미발동 (strict <)
    assert p.should_veto_rise_pct(Decimal("-5")) is False
    assert p.should_veto_rise_pct(Decimal("5")) is False
    assert p.should_veto_rise_pct(None) is False


def test_should_veto_rise_pct_noop_when_policy_zero():
    """기본 spec(zero) 에선 어떤 하락도 veto 하지 않는다."""
    p = ScoringPolicy.load_default()
    assert p.should_veto_rise_pct(Decimal("-50")) is False


def test_should_veto_normalized_uses_same_threshold(tmp_path, make_spec):
    """normalized(0~1) 경로도 동일 임계: 0.5 + (-10)/(2*20) = 0.25 미만만 veto."""
    p = ScoringPolicy.load(str(_veto_spec(tmp_path, make_spec)))
    assert p.should_veto_normalized(Decimal("0.20")) is True   # rise -12%
    assert p.should_veto_normalized(Decimal("0.25")) is False  # rise -10% 경계
    assert p.should_veto_normalized(Decimal("0.375")) is False # rise -5%
    assert p.should_veto_normalized(None) is False


@pytest.mark.parametrize("rise,expected", [
    ("0", "0.5"), ("20", "1"), ("-20", "0"), ("-10", "0.25"),
    ("10", "0.75"), ("50", "1"), ("-50", "0"),  # cap 밖은 clip
])
def test_normalized_from_rise_pct_anchors(rise, expected):
    """raw rise % → 0~1 normalized 단일 산식 (sync_service 하드코딩 /40 제거, SSoT).

    cap_threshold_pct(20) 가 yaml 에서 바뀌면 이 메서드만 따라가면 된다.
    """
    p = ScoringPolicy.load_default()
    assert p.normalized_from_rise_pct(Decimal(rise)) == Decimal(expected)
    assert p.normalized_from_rise_pct(None) is None


@pytest.mark.parametrize("rise", ["-25", "-12", "-10", "-9.99", "-5", "0", "7.5", "20"])
def test_veto_paths_equivalent_raw_and_normalized(tmp_path, make_spec, rise):
    """raw %(buy_criteria 경로) ↔ normalized(sync_service 경로) veto 판정 등가성 (패널 권고).

    normalized = 0.5 + rise_pct/(2*cap) 이므로 두 경로는 항상 같은 결론이어야 한다.
    cap_threshold_pct 나 매핑식이 바뀌면 두 경로가 조용히 갈라지는 회귀를 차단.
    """
    p = ScoringPolicy.load(str(_veto_spec(tmp_path, make_spec)))
    r = Decimal(rise)
    normalized = Decimal("0.5") + r / (Decimal("2") * p.ai_cap_pct)
    assert p.should_veto_rise_pct(r) == p.should_veto_normalized(normalized), (
        f"rise_pct {r} → normalized {normalized}: 두 veto 경로 판정 불일치"
    )


def test_spec_invariant_rejects_positive_veto_threshold(tmp_path, make_spec):
    """veto_threshold_pct 는 0 이하만 허용 (양수면 상승 예측까지 차단하는 설정 오류)."""
    bad = _veto_spec(tmp_path, make_spec, veto_threshold_pct=5.0)
    with pytest.raises(SpecValidationError, match="veto_threshold_pct"):
        ScoringPolicy.load(str(bad))


def test_spec_invariant_rejects_invalid_negative_policy(tmp_path, make_spec):
    """negative_policy 가 zero/veto 가 아니면 SpecValidationError."""
    bad = make_spec(tmp_path, axes={"ai": {"weight": 0.3, "max": 10.0, "cap_strategy": "fixed_pct",
                                            "cap_threshold_pct": 20.0, "missing_policy": "redistribute",
                                            "negative_policy": "ignore"}})  # invalid
    with pytest.raises(SpecValidationError, match="negative_policy"):
        ScoringPolicy.load(str(bad))
