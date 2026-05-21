"""
Pytest Configuration and Fixtures

Data Engine 테스트를 위한 공통 설정 및 픽스처.
"""

import pytest
import sys
from pathlib import Path
from unittest.mock import MagicMock, AsyncMock

# src 경로 추가
sys.path.insert(0, str(Path(__file__).parent.parent / "src"))


# ─────────────────────────────────────────────────────────────────────────────
# Score / spec 빌더 (PR 1 점수 모델 SSoT, 2026-05-21)
#
# 사용 예:
#   def test_something(make_score):
#       s = make_score(grade="D", missing_axes=("ai",))
#
#   def test_invariant(make_spec, tmp_path):
#       bad_spec_path = make_spec(tmp_path, composite_max=99.9)
#
# Default 는 PR 1 "5.90/A/STRONG" 케이스. 향후 PR 3/5 가 Score 필드 추가 시 빌더만 수정.
# ─────────────────────────────────────────────────────────────────────────────

@pytest.fixture
def make_score():
    """Score 인스턴스 factory. Default = 5.90/A/STRONG. overrides 로 필드 교체."""
    from decimal import Decimal
    from domain.recommendation.score import Score

    def _factory(**overrides):
        defaults = dict(
            ai_score=Decimal("3.00"),
            tech_score=Decimal("3.50"),
            sentiment_score=Decimal("5.00"),
            composite_score=Decimal("5.90"),
            composite_max=Decimal("7.40"),
            confidence=Decimal("0.797"),
            grade="A",
            recommendation_label="STRONG",
            missing_axes=(),
            veto_reasons=(),
            warnings=(),
        )
        defaults.update(overrides)
        return Score(**defaults)
    return _factory


@pytest.fixture
def make_spec():
    """tmp_path 안에 spec yaml 파일 생성하고 경로 반환. invariant 위반 케이스 작성에 사용."""
    import yaml

    def _default_spec():
        return {
            "spec_version": "1.0.0",
            "formula_version": "1.0.0",
            "axes": {
                "ai": {
                    "weight": 0.3, "max": 10.0, "cap_strategy": "fixed_pct",
                    "cap_threshold_pct": 20.0, "missing_policy": "zero", "negative_policy": "zero",
                },
                "technical": {
                    "weight": 0.4, "max": 3.5,
                    "components": {"golden_cross": 1.5, "rsi_below_threshold": 1.0, "macd_buy_signal": 1.0},
                    "rsi_threshold": 70, "missing_policy": "zero",
                },
                "sentiment": {
                    "weight": 0.3, "max": 10.0, "missing_policy": "zero",
                },
            },
            "composite_max": 7.4,
            "grades": {
                "S": {"min": 6.0, "label": "S"}, "A": {"min": 4.5, "label": "A"},
                "B": {"min": 3.0, "label": "B"}, "C": {"min": 1.5, "label": "C"},
                "D": {"min": 0.0, "label": "D"},
            },
            "recommendation_labels": {
                "STRONG":    {"min_confidence": 0.65, "min_tech_signals": 3, "label": "강력 추천", "emoji": "🟢"},
                "RECOMMEND": {"min_confidence": 0.45, "min_tech_signals": 2, "label": "추천", "emoji": "🟡"},
                "WATCH":     {"min_confidence": 0.30, "min_tech_signals": 1, "label": "관심 종목", "emoji": "🟠"},
                "NONE":      {"label": "추천 없음", "emoji": "⚪"},
            },
            "recommendation_filter": {
                "min_composite_score": 2.0, "max_recommendations": 5,
            },
        }

    def _factory(tmp_path, **overrides):
        spec = _default_spec()
        for k, v in (overrides or {}).items():
            if isinstance(v, dict) and isinstance(spec.get(k), dict):
                spec[k].update(v)
            else:
                spec[k] = v
        p = tmp_path / "test_spec.yaml"
        p.write_text(yaml.dump(spec), encoding="utf-8")
        return p
    return _factory


@pytest.fixture
def mock_mongodb():
    """MongoDB Mock 픽스처"""
    mock = MagicMock()
    mock.find = MagicMock(return_value=[])
    mock.find_one = MagicMock(return_value=None)
    mock.insert_one = MagicMock()
    mock.update_one = MagicMock()
    return mock


@pytest.fixture
def mock_kafka_producer():
    """Kafka Producer Mock 픽스처"""
    mock = AsyncMock()
    mock.send_and_wait = AsyncMock()
    return mock


@pytest.fixture
def mock_gcs_client():
    """GCS Client Mock 픽스처"""
    mock = MagicMock()
    mock.bucket = MagicMock()
    mock.bucket.return_value.blob = MagicMock()
    return mock


@pytest.fixture
def sample_strategy_definition():
    """샘플 전략 정의"""
    return {
        "strategy_id": "test_golden_cross",
        "name": "Test Golden Cross Strategy",
        "description": "테스트용 골든크로스 전략",
        "version": "1.0",
        "rules": [
            {
                "name": "golden_cross_buy",
                "signal_type": "buy",
                "conditions": [
                    {
                        "indicator": "sma",
                        "params": {"period": 20},
                        "operator": "crosses_above",
                        "value": "sma_50"
                    },
                    {
                        "indicator": "rsi",
                        "params": {"period": 14},
                        "operator": "lt",
                        "value": 70
                    }
                ],
                "logic": "and",
                "weight": 1.0
            },
            {
                "name": "death_cross_sell",
                "signal_type": "sell",
                "conditions": [
                    {
                        "indicator": "sma",
                        "params": {"period": 20},
                        "operator": "crosses_below",
                        "value": "sma_50"
                    }
                ],
                "logic": "and",
                "weight": 1.0
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.05,
            "take_profit_pct": 0.15,
            "max_position_pct": 0.1
        },
        "is_ai_generated": False,
        "tags": ["momentum", "trend-following"]
    }


@pytest.fixture
def sample_market_data():
    """샘플 시장 데이터"""
    return [
        {"date": "2024-01-01", "open": 100, "high": 105, "low": 99, "close": 104, "volume": 1000000},
        {"date": "2024-01-02", "open": 104, "high": 108, "low": 103, "close": 107, "volume": 1200000},
        {"date": "2024-01-03", "open": 107, "high": 110, "low": 106, "close": 109, "volume": 1100000},
    ]
