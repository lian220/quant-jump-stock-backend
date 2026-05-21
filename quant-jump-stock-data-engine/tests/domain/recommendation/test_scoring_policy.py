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
    assert p.formula_version == "1.0.0"
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
    assert p.formula_version == "1.0.0"


def test_load_missing_file_raises():
    with pytest.raises(SpecNotFoundError):
        ScoringPolicy.load("/nonexistent/path.yaml")


# ── Invariant ────────────────────────────────────────────

def _write_spec(tmp_path, override):
    base = {
        "spec_version": "1.0.0",
        "formula_version": "1.0.0",
        "axes": {
            "ai":        {"weight": 0.3, "max": 10.0, "cap_strategy": "fixed_pct",
                          "cap_threshold_pct": 20.0, "missing_policy": "zero",
                          "negative_policy": "zero"},
            "technical": {"weight": 0.4, "max": 3.5,
                          "components": {"golden_cross": 1.5, "rsi_below_threshold": 1.0,
                                         "macd_buy_signal": 1.0},
                          "rsi_threshold": 70, "missing_policy": "zero"},
            "sentiment": {"weight": 0.3, "max": 10.0, "missing_policy": "zero"},
        },
        "composite_max": 7.4,
        "grades": {"S": {"min": 6.0, "label": "S"}, "A": {"min": 4.5, "label": "A"},
                   "B": {"min": 3.0, "label": "B"}, "C": {"min": 1.5, "label": "C"},
                   "D": {"min": 0.0, "label": "D"}},
        "recommendation_labels": {"NONE": {"label": "x", "emoji": "x"}},
    }
    # deep merge override
    for k, v in (override or {}).items():
        if isinstance(v, dict) and isinstance(base.get(k), dict):
            base[k].update(v)
        else:
            base[k] = v
    import yaml
    p = tmp_path / "bad.yaml"
    p.write_text(yaml.dump(base))
    return p


def test_invariant_weights_must_sum_to_one(tmp_path):
    bad = _write_spec(tmp_path, {"axes": {"ai": {"weight": 0.5, "max": 10.0,
                                                 "cap_strategy": "fixed_pct",
                                                 "cap_threshold_pct": 20.0,
                                                 "missing_policy": "zero",
                                                 "negative_policy": "zero"}}})
    with pytest.raises(SpecValidationError, match="weights sum"):
        ScoringPolicy.load(str(bad))


def test_invariant_composite_max_matches_derived(tmp_path):
    bad = _write_spec(tmp_path, {"composite_max": 99.9})
    with pytest.raises(SpecValidationError, match="composite_max mismatch"):
        ScoringPolicy.load(str(bad))


def test_invariant_grade_thresholds_monotonic(tmp_path):
    bad = _write_spec(tmp_path, {"grades": {
        "S": {"min": 4.0, "label": "S"},
        "A": {"min": 5.0, "label": "A"},  # 단조성 위반
        "B": {"min": 3.0, "label": "B"},
        "C": {"min": 1.5, "label": "C"},
        "D": {"min": 0.0, "label": "D"},
    }})
    with pytest.raises(SpecValidationError, match="grade thresholds"):
        ScoringPolicy.load(str(bad))
