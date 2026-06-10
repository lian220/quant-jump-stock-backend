"""
Pytest Configuration and Fixtures

Data Engine 테스트를 위한 공통 설정 및 픽스처.
"""

import pytest
import sys
from pathlib import Path

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
    """Score 인스턴스 factory. ADR 0006 0~100 스케일. Default = 75/S/STRONG. overrides 로 필드 교체."""
    from decimal import Decimal
    from domain.recommendation.score import Score

    def _factory(**overrides):
        # all present, ai5/tech3.5/sent5 → composite 75 (ADR 0006 anchor)
        defaults = dict(
            ai_score=Decimal("5.00"),
            tech_score=Decimal("3.50"),
            sentiment_score=Decimal("5.00"),
            composite_score=Decimal("75.00"),
            composite_max=Decimal("100.00"),
            confidence=Decimal("0.750"),
            grade="S",
            recommendation_label="STRONG",
            missing_axes=(),
            veto_reasons=(),
            warnings=(),
            axis_contributions={
                "ai": Decimal("15.00"),
                "tech": Decimal("50.00"),
                "sentiment": Decimal("10.00"),
            },
            score_coverage=Decimal("1.000"),
            is_recommended=True,
        )
        defaults.update(overrides)
        return Score(**defaults)
    return _factory


@pytest.fixture
def make_spec():
    """tmp_path 안에 spec yaml 파일 생성하고 경로 반환. invariant 위반 케이스 작성에 사용."""
    import yaml

    def _default_spec():
        # ADR 0006 (0~100 재설계) 기본 spec
        return {
            "spec_version": "2.1.0",
            "formula_version": "2.0.0",
            "axes": {
                "ai": {
                    "weight": 0.3, "max": 10.0, "cap_strategy": "fixed_pct",
                    "cap_threshold_pct": 20.0, "missing_policy": "redistribute", "negative_policy": "zero",
                },
                "technical": {
                    "weight": 0.5, "max": 3.5,
                    "components": {"golden_cross": 1.5, "rsi_below_threshold": 1.0, "macd_buy_signal": 1.0},
                    "rsi_threshold": 70, "missing_policy": "redistribute",
                },
                "sentiment": {
                    "weight": 0.2, "max": 10.0, "raw_clip": 0.35, "missing_policy": "redistribute",
                },
            },
            "composite_max": 100.0,
            "grades": {
                # prod scoring_spec.yaml 과 동일값 (462표본 재보정, ADR 0006 부록 B)
                "S": {"min": 82.0, "label": "S"}, "A": {"min": 77.0, "label": "A"},
                "B": {"min": 68.0, "label": "B"}, "C": {"min": 58.0, "label": "C"},
                "D": {"min": 0.0, "label": "D"},
            },
            "recommendation_labels": {
                # 등급 경계 정렬 (2026-06-10 재보정): STRONG=A/RECOMMEND=B/WATCH=C.
                # min_tech_signals 게이트 제거 — tech 는 composite 50% weight 로 이미 반영 (이중 반영 금지).
                "STRONG":    {"min_confidence": 0.77, "label": "강력 추천", "emoji": "🟢"},
                "RECOMMEND": {"min_confidence": 0.68, "label": "추천", "emoji": "🟡"},
                "WATCH":     {"min_confidence": 0.58, "label": "관심 종목", "emoji": "🟠"},
                "NONE":      {"label": "추천 없음", "emoji": "⚪"},
            },
            "recommendation_filter": {
                "min_composite_score": 68.0, "max_recommendations": 5,
            },
        }

    def _deep_merge(base: dict, overrides: dict) -> dict:
        """중첩 dict 재귀 머지 — axes.ai 내부 키 1개만 override 가능 (리뷰 M4)."""
        result = dict(base)
        for k, v in overrides.items():
            if isinstance(v, dict) and isinstance(result.get(k), dict):
                result[k] = _deep_merge(result[k], v)
            else:
                result[k] = v
        return result

    def _factory(tmp_path, **overrides):
        spec = _deep_merge(_default_spec(), overrides or {})
        p = tmp_path / "test_spec.yaml"
        p.write_text(yaml.dump(spec), encoding="utf-8")
        return p
    return _factory


# cleanup PR (2026-05-22): mock_mongodb / mock_kafka_producer / mock_gcs_client 픽스처 제거.
# - mock_mongodb: 사용처 0 — 실 테스트는 MagicMock 직접 (test_sync_integration.py 패턴) 또는 unittest.mock.patch 사용
# - mock_kafka_producer: Kafka 폐기 (Pub/Sub 전환, 2026-02-14) 후 잔재
# - mock_gcs_client: 사용처 0
# sample_market_data 도 사용처 0 이라 제거.

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
