"""
CompositeGrade - 종합 점수 등급 관리 (Domain Layer)

Kotlin Core API의 CompositeGrade enum과 1:1 매칭되는 Python 등급 시스템.
두 가지 등급 체계를 통합 관리:

1. CompositeGrade (S/A/B/C/D): PostgreSQL 저장용, 0-7.4 스케일
   - Kotlin `CompositeGrade` enum과 동일 기준
   - sync_service → prediction_results 테이블에 저장

2. RecommendationGrade (강력추천/추천/관심종목): Slack 리포트용, confidence 기반
   - buy_criteria → comprehensive_report → Slack 알림에 표시
   - confidence(%) + tech_signals 조합으로 결정
"""
from decimal import Decimal
from enum import Enum
from typing import Dict, Any


def _spec_label(key: str) -> str:
    """recommendation_labels[key].label — ScoringPolicy lazy 로드."""
    # 순환 import 방지: 함수 내부 import
    from domain.recommendation.scoring_policy import ScoringPolicy
    return ScoringPolicy.load_default().label_metadata(key)["label"]


def _spec_emoji(key: str) -> str:
    from domain.recommendation.scoring_policy import ScoringPolicy
    return ScoringPolicy.load_default().label_metadata(key)["emoji"]


# ═══════════════════════════════════════════════════════════════
# 1. CompositeGrade: PostgreSQL 저장용 등급 (Kotlin 매칭)
# ═══════════════════════════════════════════════════════════════

class CompositeGrade(Enum):
    """
    Composite Score 기반 등급 (Kotlin CompositeGrade enum 매칭).

    Composite Score 최대값 = 0.3×AI(10) + 0.4×Tech(3.5) + 0.3×Sentiment(10) = 7.4
    """
    S = ("6.0 ~ 7.5", "매우 강한 매수 신호", "emerald")
    A = ("4.5 ~ 5.9", "강한 매수 신호", "cyan")
    B = ("3.0 ~ 4.4", "보통 매수 신호", "yellow")
    C = ("1.5 ~ 2.9", "약한 매수 신호", "orange")
    D = ("0.0 ~ 1.4", "매수 신호 없음", "red")

    def __init__(self, score_range: str, description: str, color: str):
        self.score_range = score_range
        self.description = description
        self.color = color

    @staticmethod
    def from_score(composite_score: Decimal) -> "CompositeGrade":
        """Composite Score(0-7.4) → 등급 판정"""
        if composite_score >= Decimal("6.0"):
            return CompositeGrade.S
        elif composite_score >= Decimal("4.5"):
            return CompositeGrade.A
        elif composite_score >= Decimal("3.0"):
            return CompositeGrade.B
        elif composite_score >= Decimal("1.5"):
            return CompositeGrade.C
        else:
            return CompositeGrade.D

    @staticmethod
    def from_string(value: str) -> "CompositeGrade":
        """문자열 → CompositeGrade (Kotlin fromString 매칭)"""
        try:
            return CompositeGrade[value.upper()]
        except KeyError:
            raise ValueError(f"Invalid grade: {value}")


# ═══════════════════════════════════════════════════════════════
# 2. RecommendationGrade: Slack 리포트용 등급
# ═══════════════════════════════════════════════════════════════

class RecommendationGrade(Enum):
    """
    Slack 리포트 표시용 추천 등급.

    confidence(%) + tech_signals 조합으로 결정.
    buy_criteria.filter_candidates() → comprehensive_report → Slack 알림에서 사용.

    PR 2 (2026-05-21): label / emoji 는 `scoring_spec.yaml.recommendation_labels` SSoT.
    enum 인스턴스 자체는 priority 만 정적, label/emoji 는 `ScoringPolicy.label_metadata` 에서
    lazy 로드 (spec 갱신 시 재배포만으로 반영).
    """
    STRONG = 0
    RECOMMEND = 1
    WATCH = 2
    NONE = 3

    def __init__(self, priority: int):
        self.priority = priority

    @property
    def label(self) -> str:
        return _spec_label(self.name)

    @property
    def emoji(self) -> str:
        return _spec_emoji(self.name)

    @staticmethod
    def from_scores(scores: Dict[str, Any]) -> "RecommendationGrade":
        """
        confidence + tech_signals 기반 추천 등급 결정.

        강력 추천: confidence >= 65% AND tech_signals >= 3
        추천:       confidence >= 45% AND tech_signals >= 2
        관심 종목:  confidence >= 30% AND tech_signals >= 1
        추천 없음:  그 외
        """
        confidence = scores.get("confidence", 0)
        tech_signals = scores.get("tech_signals", 0)

        if confidence >= 0.65 and tech_signals >= 3:
            return RecommendationGrade.STRONG
        if confidence >= 0.45 and tech_signals >= 2:
            return RecommendationGrade.RECOMMEND
        if confidence >= 0.30 and tech_signals >= 1:
            return RecommendationGrade.WATCH
        return RecommendationGrade.NONE


# ScoreScale 클래스는 PR 2 (2026-05-21) 에서 완전 제거됨.
# 모든 상수는 `scoring_spec.yaml` + `ScoringPolicy` 가 SSoT.
# - 가중치/max: policy.axes
# - composite_max: policy.composite_max
# - rsi_threshold: policy.rsi_threshold
# - ai_cap_pct: policy.ai_cap_pct
