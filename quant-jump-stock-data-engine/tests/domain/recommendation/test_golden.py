"""Golden CSV — ScoringPolicy.compose_components 결과 검증.

본 CSV 는 spec_version 2.0.0 (ADR 0006 0~100 재설계) 의 frozen 동작.
손으로 검증한 anchor 케이스 + 등급 경계 케이스로 구성. expected 값은 직접 계산.
Spec 변경 시 본 CSV 도 명시적 수정 필요 (silent drift 방지).

산식: composite = (Σ_present w_renorm_k * (score_k/max_k)) * 100, composite_max=100.
"""
import csv
from decimal import Decimal
from pathlib import Path

import pytest

from domain.recommendation.scoring_policy import ScoringPolicy


GOLDEN_PATH = Path(__file__).parents[2] / "data" / "golden_v2_0_0.csv"


def _b(v: str) -> bool:
    return v.strip().lower() == "true"


@pytest.fixture(scope="module")
def policy():
    return ScoringPolicy.load_default()


def _load_cases():
    with open(GOLDEN_PATH, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


@pytest.mark.parametrize(
    "case",
    _load_cases(),
    ids=lambda c: f"{c['case_id']}-{c['scenario']}",
)
def test_golden(case, policy):
    s = policy.compose_components(
        ai_score=Decimal(case["ai_score"]),
        tech_score=Decimal(case["tech_score"]),
        sentiment_score=Decimal(case["sentiment_score"]),
        has_ai=_b(case["has_ai"]),
        has_tech=_b(case["has_tech"]),
        has_sentiment=_b(case["has_sentiment"]),
        tech_signal_count=int(case["tech_signals"]),
    )
    expected_composite = Decimal(case["expected_composite"])
    assert abs(s.composite_score - expected_composite) <= Decimal("0.01"), (
        f"{case['case_id']}-{case['scenario']} composite {s.composite_score} "
        f"!= {expected_composite}"
    )
    assert s.grade == case["expected_grade"], (
        f"{case['case_id']}-{case['scenario']} grade {s.grade} "
        f"!= {case['expected_grade']}"
    )
    assert s.recommendation_label == case["expected_label"], (
        f"{case['case_id']}-{case['scenario']} label {s.recommendation_label} "
        f"!= {case['expected_label']}"
    )
    assert s.is_recommended == _b(case["expected_is_recommended"]), (
        f"{case['case_id']}-{case['scenario']} is_recommended {s.is_recommended} "
        f"!= {case['expected_is_recommended']}"
    )
