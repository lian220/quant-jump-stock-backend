"""MacroGateRunner unit tests (refactor 2026-05-22)."""
from decimal import Decimal
from unittest.mock import MagicMock

import pytest

from domain.recommendation.scoring_policy import ScoringPolicy
from services.macro_gates import MacroGateRunner, MacroGateResult, VixGate, _fetch_vix


def _mock_db_with_vix(vix_value):
    """daily_stock_data.find_one 가 yfinance_indicators.^VIX 반환하는 mock."""
    db = MagicMock()
    if vix_value is None:
        db.daily_stock_data.find_one.return_value = None
    elif isinstance(vix_value, dict):
        db.daily_stock_data.find_one.return_value = {
            "yfinance_indicators": {"^VIX": vix_value}
        }
    else:
        db.daily_stock_data.find_one.return_value = {
            "yfinance_indicators": {"^VIX": vix_value}
        }
    return db


def test_runner_evaluates_vix_gate_normal_pass():
    """정상 VIX → no block → None 반환."""
    policy = ScoringPolicy.load_default()
    db = _mock_db_with_vix(18.06)
    runner = MacroGateRunner(policy=policy, db=db)
    result = runner.evaluate("2026-05-19")
    assert result is None  # 차단 gate 없음 = None


def test_runner_evaluates_vix_gate_blocked():
    """VIX > threshold → block → MacroGateResult(blocked=True)."""
    policy = ScoringPolicy.load_default()
    db = _mock_db_with_vix(40.0)
    runner = MacroGateRunner(policy=policy, db=db)
    result = runner.evaluate("2026-05-22")
    assert result is not None
    assert result.gate_name == "vix"
    assert result.blocked is True
    assert result.value == 40.0
    assert result.threshold == 35.0
    assert "변동성" in result.reason


def test_runner_returns_none_when_vix_missing_skip_policy():
    """VIX 결손 + skip default → block 안 함 → None."""
    policy = ScoringPolicy.load_default()
    db = _mock_db_with_vix(None)
    runner = MacroGateRunner(policy=policy, db=db)
    result = runner.evaluate("2026-05-04")
    assert result is None


def test_fetch_vix_scalar_value():
    """yfinance_indicators.^VIX 가 scalar (float) 일 때."""
    db = MagicMock()
    db.daily_stock_data.find_one.return_value = {
        "yfinance_indicators": {"^VIX": 18.06}
    }
    assert _fetch_vix(db, "2026-05-19") == 18.06


def test_fetch_vix_dict_with_close_price():
    """yfinance_indicators.^VIX 가 dict 일 때 close_price 우선."""
    db = MagicMock()
    db.daily_stock_data.find_one.return_value = {
        "yfinance_indicators": {"^VIX": {"close_price": 19.5, "close": 19.7}}
    }
    assert _fetch_vix(db, "2026-05-19") == 19.5


def test_fetch_vix_dict_with_close_fallback():
    """close_price 없으면 close fallback."""
    db = MagicMock()
    db.daily_stock_data.find_one.return_value = {
        "yfinance_indicators": {"^VIX": {"close": 20.1}}
    }
    assert _fetch_vix(db, "2026-05-19") == 20.1


def test_fetch_vix_returns_none_when_doc_missing():
    """doc 없으면 None."""
    db = MagicMock()
    db.daily_stock_data.find_one.return_value = None
    assert _fetch_vix(db, "2026-05-19") is None


def test_fetch_vix_swallows_mongo_error():
    """Mongo 예외 시 None 반환 + log (silent error 방지)."""
    db = MagicMock()
    db.daily_stock_data.find_one.side_effect = ConnectionError("mongo timeout")
    assert _fetch_vix(db, "2026-05-19") is None


def test_vix_gate_disabled_when_policy_disabled(tmp_path, make_spec):
    """spec.macro_gates.vix.enabled=False 시 gate 미평가."""
    spec_path = make_spec(tmp_path, macro_gates={
        "vix": {"enabled": False, "threshold": 35.0},
    })
    policy = ScoringPolicy.load(str(spec_path))
    db = _mock_db_with_vix(99.0)  # 임계 훨씬 초과
    runner = MacroGateRunner(policy=policy, db=db)
    result = runner.evaluate("2026-05-22")
    assert result is None  # gate 비활성 → 평가 skip
