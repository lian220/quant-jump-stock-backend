"""Score — 점수 계산 결과 (frozen, immutable).

ScoringPolicy 의 점수 계산 메서드들이 반환하는 값 객체.
모든 collection 필드는 tuple 로 강제하여 변경 불가.

필드 의미 (리뷰 #7 반영):
  - missing_axes: 데이터 부재 축 ("ai" / "tech" / "sentiment")
  - veto_reasons: 정책상 추천 제외 사유 (PR 3 에서 "ai_negative" 등 추가)
  - warnings: 사용자 노출용 경고 (PR 5 에서 VIX 등)
"""
from dataclasses import dataclass, field
from decimal import Decimal
from typing import Tuple


@dataclass(frozen=True, slots=True)
class Score:
    ai_score: Decimal
    tech_score: Decimal
    sentiment_score: Decimal
    composite_score: Decimal
    composite_max: Decimal
    confidence: Decimal
    grade: str                       # "S" / "A" / "B" / "C" / "D"
    recommendation_label: str        # "STRONG" / "RECOMMEND" / "WATCH" / "NONE"
    missing_axes: Tuple[str, ...] = field(default=())
    veto_reasons: Tuple[str, ...] = field(default=())
    warnings: Tuple[str, ...] = field(default=())
