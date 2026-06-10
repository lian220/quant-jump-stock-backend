"""Score — 점수 계산 결과 (frozen, immutable).

ScoringPolicy 의 점수 계산 메서드들이 반환하는 값 객체.
모든 collection 필드는 tuple 로 강제하여 변경 불가.

필드 의미 (리뷰 #7 반영):
  - missing_axes: 데이터 부재 축 ("ai" / "tech" / "sentiment")
  - veto_reasons: 정책상 추천 제외 사유 (PR 3 에서 "ai_negative" 등 추가)
  - warnings: 사용자 노출용 경고 (PR 5 에서 VIX 등)

ADR 0006 (0~100 재설계) 추가 필드:
  - axis_contributions: present 축별 기여 점수 dict {축: w_renorm*norm*100}. 합 ≈ composite (XAI §2.9)
  - score_coverage: present 축들의 원본 weight 합 (재정규화 전). 전부 present → 1.0
  - is_recommended: coverage guard 통과 여부 (§2.4)
"""
from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal


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
    missing_axes: tuple[str, ...] = field(default=())
    veto_reasons: tuple[str, ...] = field(default=())
    warnings: tuple[str, ...] = field(default=())
    # ADR 0006 (0~100 재설계): XAI 기여도 / 커버리지 / 추천 가능 여부
    # dict 는 frozen 등호/해시 비교에서 문제될 수 있어 compare=False (값 비교는 composite 등 스칼라로 충분)
    axis_contributions: dict[str, Decimal] = field(default_factory=dict, compare=False)
    score_coverage: Decimal = field(default=Decimal("0"))
    is_recommended: bool = field(default=True)
