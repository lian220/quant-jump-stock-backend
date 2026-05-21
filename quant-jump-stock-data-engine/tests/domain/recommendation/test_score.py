"""Score frozen dataclass 검증 — 불변성 + tuple 필드."""
from dataclasses import FrozenInstanceError
from decimal import Decimal

import pytest

from domain.recommendation.score import Score


def test_score_is_frozen(make_score):
    s = make_score()
    with pytest.raises(FrozenInstanceError):
        s.composite_score = Decimal("0")  # type: ignore


def test_score_missing_axes_is_tuple(make_score):
    s = make_score()
    assert isinstance(s.missing_axes, tuple)
    assert isinstance(s.veto_reasons, tuple)
    assert isinstance(s.warnings, tuple)


def test_score_separates_missing_and_veto(make_score):
    """data missing 과 정책상 veto 는 별도 필드."""
    s = make_score(
        ai_score=Decimal("0"),
        sentiment_score=Decimal("0"),
        composite_score=Decimal("1.40"),
        confidence=Decimal("0.189"),
        grade="D",
        recommendation_label="NONE",
        missing_axes=("sentiment",),       # 데이터 없음
        veto_reasons=(),                    # PR 3 에서 ai_negative_prediction 등 추가
    )
    assert "sentiment" in s.missing_axes
    assert s.veto_reasons == ()
