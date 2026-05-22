"""ScoringPolicy — spec load + invariant + public API."""
import os
from decimal import Decimal
from pathlib import Path
import pytest
from domain.recommendation.scoring_policy import ScoringPolicy
from domain.recommendation.exceptions import SpecValidationError, SpecNotFoundError


# ── Spec load 경로 ──────────────────────────────────────

def test_load_default_finds_backend_repo_root_spec(monkeypatch):
    """본 plan default 경로 — backend repo 루트 scoring_spec.yaml."""
    # SCORING_SPEC_PATH 환경변수가 외부에서 leak 되어 있으면 default 경로 검증이 무의미
    monkeypatch.delenv("SCORING_SPEC_PATH", raising=False)
    ScoringPolicy._cached_default.cache_clear()  # 환경 영향 차단
    p = ScoringPolicy.load_default()
    assert p.formula_version == "1.1.0"
    assert p.composite_max == Decimal("7.4")


def test_load_via_env_path(tmp_path, monkeypatch):
    """SCORING_SPEC_PATH env 우선 (리뷰 #1)."""
    # backend repo 루트의 실제 spec 파일을 복사해서 사용
    # tests/domain/recommendation/ → parents[4] = backend repo root
    real_spec = Path(__file__).resolve().parents[4] / "scoring_spec.yaml"
    assert real_spec.exists(), f"real spec must exist at {real_spec}"
    custom = tmp_path / "custom_spec.yaml"
    custom.write_text(real_spec.read_text(encoding="utf-8"), encoding="utf-8")
    monkeypatch.setenv("SCORING_SPEC_PATH", str(custom))
    ScoringPolicy._cached_default.cache_clear()
    p = ScoringPolicy.load_default()
    assert p.formula_version == "1.1.0"


def test_load_missing_file_raises():
    with pytest.raises(SpecNotFoundError):
        ScoringPolicy.load("/nonexistent/path.yaml")


# ── Invariant ────────────────────────────────────────────

def test_invariant_weights_must_sum_to_one(make_spec, tmp_path):
    bad = make_spec(tmp_path, axes={"ai": {"weight": 0.5, "max": 10.0,
                                           "cap_strategy": "fixed_pct",
                                           "cap_threshold_pct": 20.0,
                                           "missing_policy": "zero",
                                           "negative_policy": "zero"}})
    with pytest.raises(SpecValidationError, match="weights sum"):
        ScoringPolicy.load(str(bad))


def test_invariant_composite_max_matches_derived(make_spec, tmp_path):
    bad = make_spec(tmp_path, composite_max=99.9)
    with pytest.raises(SpecValidationError, match="composite_max mismatch"):
        ScoringPolicy.load(str(bad))


def test_invariant_grade_thresholds_monotonic(make_spec, tmp_path):
    bad = make_spec(tmp_path, grades={
        "S": {"min": 4.0, "label": "S"},
        "A": {"min": 5.0, "label": "A"},  # 단조성 위반
        "B": {"min": 3.0, "label": "B"},
        "C": {"min": 1.5, "label": "C"},
        "D": {"min": 0.0, "label": "D"},
    })
    with pytest.raises(SpecValidationError, match="grade thresholds"):
        ScoringPolicy.load(str(bad))


# ── compose_components / score_from_raw_signals 현행 동작 검증 ──────────────


def test_score_all_signals_strong_matches_current():
    """현행 sync_service 결과와 동일: AI=10, Tech=3.5, Sent=5 → composite 5.9 / grade A."""
    p = ScoringPolicy.load_default()
    s = p.compose_components(
        ai_score=Decimal("10"),
        tech_score=Decimal("3.5"),
        sentiment_score=Decimal("5"),
        has_ai=True,
        has_tech=True,
        has_sentiment=True,
        tech_signal_count=3,
    )
    assert s.composite_score == Decimal("5.90")
    assert s.composite_max == Decimal("7.40")
    assert s.confidence == Decimal("0.797")
    assert s.grade == "A"
    assert s.recommendation_label == "STRONG"  # confidence 0.797 >= 0.65, signals 3 >= 3
    assert s.missing_axes == ()


def test_score_sentiment_missing_zero_policy_preserved():
    """본 PR: sentiment N/A → 0점 처리 보존 (PR 4 에서 재분배 적용)."""
    p = ScoringPolicy.load_default()
    s = p.compose_components(
        ai_score=Decimal("10"),
        tech_score=Decimal("3.5"),
        sentiment_score=Decimal("0"),
        has_ai=True,
        has_tech=True,
        has_sentiment=False,         # 누락
        tech_signal_count=3,
    )
    # 현행: 0.3*10 + 0.4*3.5 + 0.3*0 = 4.4. composite_max 는 7.4 유지 (재분배 없음)
    assert s.composite_score == Decimal("4.40")
    assert s.composite_max == Decimal("7.40")
    assert "sentiment" in s.missing_axes


def test_score_raw_rise_pct_20_pct_full_score():
    """raw +20% → AI score 10 (현행 cap 동작). compose_components 직접 호출 (cleanup PR 2026-05-22)."""
    p = ScoringPolicy.load_default()
    ai_s = p.normalize_rise_pct_to_score(Decimal("20"))
    tech_s = p.tech_score_from_indicators({"golden_cross": True, "rsi": 50, "macd_buy_signal": True})
    sent_s = p.sentiment_score_from_raw(Decimal("0.5"))
    score = p.compose_components(
        ai_score=ai_s, tech_score=tech_s, sentiment_score=sent_s,
        has_ai=True, has_tech=True, has_sentiment=True,
        tech_signal_count=3,
    )
    # AI=10, Tech=3.5, Sent=5 → composite 5.9
    assert score.composite_score == Decimal("5.90")


def test_score_negative_rise_pct_zero_policy_preserved():
    """PR 3b (2026-05-22): -15% → veto 발동. composite=0, grade=D, label=NONE."""
    p = ScoringPolicy.load_default()
    ai_s = p.normalize_rise_pct_to_score(Decimal("-15"))
    tech_s = p.tech_score_from_indicators({"golden_cross": True, "rsi": 50, "macd_buy_signal": True})
    sent_s = p.sentiment_score_from_raw(Decimal("0.5"))
    score = p.compose_components(
        ai_score=ai_s, tech_score=tech_s, sentiment_score=sent_s,
        has_ai=True, has_tech=True, has_sentiment=True, tech_signal_count=3,
        veto_reasons=("ai_negative",),  # caller (sync_service / buy_criteria) 가 raw<0 검사 후 전달
    )
    # PR 3b: rise<0 → veto 발동 → composite=0, grade=D, label=NONE, veto_reasons=("ai_negative",)
    assert score.composite_score == Decimal("0.00")
    assert score.grade == "D"
    assert score.recommendation_label == "NONE"
    assert score.veto_reasons == ("ai_negative",)


def test_ai_score_from_normalized_matches_current_sync_service():
    """sync_service 에서 normalized 1.0 → 10 (max)."""
    p = ScoringPolicy.load_default()
    assert p.ai_score_from_normalized(Decimal("1.0")) == Decimal("10.00")
    assert p.ai_score_from_normalized(Decimal("0.5")) == Decimal("0")
    assert p.ai_score_from_normalized(Decimal("0.75")) == Decimal("5.00")
    assert p.ai_score_from_normalized(None) == Decimal("0")


def test_label_metadata_returns_spec_label_emoji():
    """PR 2: label_metadata 가 spec 의 label/emoji 반환."""
    p = ScoringPolicy.load_default()
    strong = p.label_metadata("STRONG")
    assert strong["label"] == "강력 추천"
    assert strong["emoji"] == "🟢"
    none = p.label_metadata("NONE")
    assert none["label"] == "추천 없음"
    assert none["emoji"] == "⚪"


def test_label_metadata_unknown_key_falls_back_to_none():
    """unknown key → NONE 매핑 (없으면 default)."""
    p = ScoringPolicy.load_default()
    out = p.label_metadata("UNKNOWN_GRADE")
    assert out["label"] == "추천 없음"
    assert out["emoji"] == "⚪"
