"""PR 5: VIX 거시 gate unit tests."""
from decimal import Decimal

import pytest

from domain.recommendation.scoring_policy import ScoringPolicy
from domain.recommendation.exceptions import SpecValidationError


def test_default_spec_has_vix_gate_enabled():
    """PR 5 spec → VIX gate 활성."""
    p = ScoringPolicy.load_default()
    assert p.is_vix_gate_enabled() is True
    assert p.vix_threshold == Decimal("35.0")


def test_should_block_above_threshold():
    """VIX > 35 → True (차단)."""
    p = ScoringPolicy.load_default()
    assert p.should_block_on_vix(40.0) is True
    assert p.should_block_on_vix(35.1) is True


def test_should_not_block_at_or_below_threshold():
    """VIX ≤ 35 → False (정상)."""
    p = ScoringPolicy.load_default()
    assert p.should_block_on_vix(35.0) is False
    assert p.should_block_on_vix(17.5) is False
    assert p.should_block_on_vix(0) is False


def test_missing_vix_skip_policy_default():
    """VIX 결손 + missing_policy=skip → 정상 진행 (False)."""
    p = ScoringPolicy.load_default()
    assert p.should_block_on_vix(None) is False


def test_missing_vix_block_policy(tmp_path, make_spec):
    """missing_policy=block 일 때는 VIX 결손도 차단."""
    spec_path = make_spec(tmp_path, macro_gates={
        "vix": {
            "enabled": True,
            "threshold": 35.0,
            "missing_policy": "block",
        },
    })
    policy = ScoringPolicy.load(str(spec_path))
    assert policy.should_block_on_vix(None) is True
    assert policy.should_block_on_vix(20.0) is False  # 정상 범위는 여전히 진행


def test_gate_disabled_returns_false(tmp_path, make_spec):
    """enabled=False 면 어떤 VIX 값이든 차단 안 함."""
    spec_path = make_spec(tmp_path, macro_gates={
        "vix": {"enabled": False, "threshold": 10.0},  # 매우 낮은 임계여도 비활성
    })
    policy = ScoringPolicy.load(str(spec_path))
    assert policy.is_vix_gate_enabled() is False
    assert policy.vix_threshold is None
    assert policy.should_block_on_vix(99.0) is False


def test_no_macro_gates_section_means_disabled(tmp_path, make_spec):
    """spec 에 macro_gates 섹션 없으면 gate 비활성 (backward compat)."""
    spec_path = make_spec(tmp_path)  # default 에 macro_gates 없음
    policy = ScoringPolicy.load(str(spec_path))
    assert policy.is_vix_gate_enabled() is False
    assert policy.should_block_on_vix(99.0) is False


def test_invariant_rejects_negative_threshold(tmp_path, make_spec):
    """threshold ≤ 0 시 SpecValidationError."""
    bad = make_spec(tmp_path, macro_gates={
        "vix": {"enabled": True, "threshold": -1.0},
    })
    with pytest.raises(SpecValidationError, match="threshold"):
        ScoringPolicy.load(str(bad))


def test_invariant_rejects_invalid_missing_policy(tmp_path, make_spec):
    """missing_policy 가 skip/block 외 값이면 거부."""
    bad = make_spec(tmp_path, macro_gates={
        "vix": {"enabled": True, "threshold": 35.0, "missing_policy": "ignore"},
    })
    with pytest.raises(SpecValidationError, match="missing_policy"):
        ScoringPolicy.load(str(bad))
