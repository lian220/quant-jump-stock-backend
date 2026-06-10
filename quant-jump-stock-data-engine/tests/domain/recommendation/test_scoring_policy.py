"""ScoringPolicy — spec load + invariant + public API."""
import os
from decimal import Decimal
from pathlib import Path
import pytest
from domain.recommendation.scoring_policy import ScoringPolicy
from domain.recommendation.exceptions import SpecValidationError, SpecNotFoundError


# ── Spec load 경로 ──────────────────────────────────────

def test_load_default_finds_backend_repo_root_spec(monkeypatch):
    """본 plan default 경로 — backend repo 루트 scoring_spec.yaml (ADR 0006: 2.0.0)."""
    # SCORING_SPEC_PATH 환경변수가 외부에서 leak 되어 있으면 default 경로 검증이 무의미
    monkeypatch.delenv("SCORING_SPEC_PATH", raising=False)
    ScoringPolicy._cached_default.cache_clear()  # 환경 영향 차단
    p = ScoringPolicy.load_default()
    assert p.formula_version == "2.0.0"
    assert p.composite_max == Decimal("100.0")


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
    assert p.formula_version == "2.0.0"


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


# ── compose_components 동작 검증 (ADR 0006 0~100) ──────────────


def test_score_all_signals_strong_0_to_100():
    """AI=10, Tech=3.5, Sent=5 → norm 1.0/1.0/0.5 → composite 90 (ADR 0006)."""
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
    # (0.3*1.0 + 0.5*1.0 + 0.2*0.5)*100 = (0.3+0.5+0.1)*100 = 90
    assert s.composite_score == Decimal("90.00")
    assert s.composite_max == Decimal("100.00")
    assert s.confidence == Decimal("0.900")
    assert s.grade == "S"
    assert s.recommendation_label == "STRONG"
    assert s.missing_axes == ()
    assert s.score_coverage == Decimal("1.000")
    assert s.is_recommended is True
    # axis_contributions 합 ≈ composite
    total = sum(s.axis_contributions.values())
    assert abs(total - s.composite_score) <= Decimal("0.01")


def test_score_sentiment_missing_redistributes():
    """ADR 0006 §2.4: sentiment 결측 → 축 제외 + weight 재정규화 (0점 벌점 금지)."""
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
    # ai 1.0, tech 1.0, renorm w_ai=0.3/0.8=0.375, w_tech=0.5/0.8=0.625 → 100
    assert s.composite_score == Decimal("100.00")
    assert s.composite_max == Decimal("100.00")
    assert "sentiment" in s.missing_axes
    assert s.score_coverage == Decimal("0.800")
    assert s.is_recommended is True  # available_axes=2


def test_score_tech_only_not_recommended():
    """ADR 0006 §2.4: tech 단일축 → composite 계산되나 추천 불가 (label NONE)."""
    p = ScoringPolicy.load_default()
    s = p.compose_components(
        ai_score=Decimal("0"), tech_score=Decimal("3.5"), sentiment_score=Decimal("0"),
        has_ai=False, has_tech=True, has_sentiment=False,
        tech_signal_count=3,
    )
    assert s.composite_score == Decimal("100.00")
    assert s.is_recommended is False  # available 1, coverage 0.5 < 0.8
    assert s.recommendation_label == "NONE"
    assert s.score_coverage == Decimal("0.500")


def test_score_no_tech_not_recommended():
    """ADR 0006 §2.4: has_tech=False → 추천 불가."""
    p = ScoringPolicy.load_default()
    s = p.compose_components(
        ai_score=Decimal("10"), tech_score=Decimal("0"), sentiment_score=Decimal("10"),
        has_ai=True, has_tech=False, has_sentiment=True,
        tech_signal_count=0,
    )
    assert s.is_recommended is False
    assert s.recommendation_label == "NONE"


def test_score_raw_rise_pct_neutral_continuous():
    """ADR 0006 §2.3: 컷오프 제거 — 중립/하락도 연속 점수."""
    p = ScoringPolicy.load_default()
    # rise 0% → normalized 0.5 → ai 5.0 (중립, 0점 아님)
    assert p.normalize_rise_pct_to_score(Decimal("0")) == Decimal("5.00")
    # rise +20% → normalized 1.0 → 10
    assert p.normalize_rise_pct_to_score(Decimal("20")) == Decimal("10.00")
    # rise -20% → normalized 0.0 → 0
    assert p.normalize_rise_pct_to_score(Decimal("-20")) == Decimal("0.00")
    # rise -10% → normalized 0.25 → 2.5 (하락도 0점 아닌 연속 저점수)
    assert p.normalize_rise_pct_to_score(Decimal("-10")) == Decimal("2.50")


def test_sentiment_score_from_raw_linear_mapping():
    """ADR 0006 §2.3: sentiment 선형 매핑 — 중립 raw 0 → 5.0, ±0.35 → 10/0."""
    p = ScoringPolicy.load_default()
    assert p.sentiment_score_from_raw(Decimal("0")) == Decimal("5.00")
    assert p.sentiment_score_from_raw(Decimal("0.35")) == Decimal("10.00")
    assert p.sentiment_score_from_raw(Decimal("-0.35")) == Decimal("0.00")
    # clip: 0.7 → clamp 0.35 → 10
    assert p.sentiment_score_from_raw(Decimal("0.7")) == Decimal("10.00")
    assert p.sentiment_score_from_raw(None) == Decimal("0")


def test_ai_score_from_normalized_continuous():
    """ADR 0006: 컷오프 제거 — normalized * max (연속)."""
    p = ScoringPolicy.load_default()
    assert p.ai_score_from_normalized(Decimal("1.0")) == Decimal("10.00")
    assert p.ai_score_from_normalized(Decimal("0.5")) == Decimal("5.00")  # 중립 (0점 아님)
    assert p.ai_score_from_normalized(Decimal("0.75")) == Decimal("7.50")
    assert p.ai_score_from_normalized(Decimal("0.25")) == Decimal("2.50")  # 하락도 연속
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
