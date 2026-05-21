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
    """
    STRONG = ("강력 추천", "🟢", 0)
    RECOMMEND = ("추천", "🟡", 1)
    WATCH = ("관심 종목", "🟠", 2)
    NONE = ("추천 없음", "⚪", 3)

    def __init__(self, label: str, emoji: str, priority: int):
        self.label = label
        self.emoji = emoji
        self.priority = priority

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


# ═══════════════════════════════════════════════════════════════
# 3. 점수 스케일 상수
# ═══════════════════════════════════════════════════════════════

class ScoreScale:
    """
    ⚠ DEPRECATED (PR 1, 2026-05-21): ScoringPolicy.axes 가 SSoT.

    이 클래스는 PR 2 에서 완전 삭제 예정. 본 PR (점수 모델 SSoT 중앙화) 머지 시점부터
    이 클래스의 모든 상수는 `scoring_spec.yaml` + `ScoringPolicy` 가 단일 진실원이다.
    호출처가 모두 ScoringPolicy public API 로 이전 완료 (Task 2.1/2.2/2.3):
    - sync_service → policy.compose_components / ai_score_from_normalized 등
    - buy_criteria → policy.tech_score_from_indicators / compose_components
    - slack_notifier → policy.composite_max / label_metadata
    - comprehensive_report → policy.normalize_rise_pct_to_score / sentiment_score_from_raw

    상수 자체는 외부 import (deprecated) 와 PR 1 머지 후 1주일 호환을 위해 유지.
    PR 2 에서 본 클래스 + 위 import 모두 삭제.

    구 의미 (참고):
    - sync_service 스케일: AI(0-10), Tech(0-3.5), Sentiment(0-10) → 최대 7.4
    - buy_criteria 스케일: AI(0-3.5), Tech(0-3.5), Sentiment(0-1) → 최대 ~2.75
    """

    # ── sync_service 스케일 (PostgreSQL 저장) ──
    SYNC_MAX_AI = Decimal("10")         # rise_probability × 10
    SYNC_MAX_TECH = Decimal("3.5")      # golden_cross(1.5) + RSI(1.0) + MACD(1.0)
    SYNC_MAX_SENTIMENT = Decimal("10")  # (sentiment+1)/2 × 10
    SYNC_MAX_COMPOSITE = Decimal("7.4") # 0.3×10 + 0.4×3.5 + 0.3×10

    # ── buy_criteria 스케일 (Slack 리포트) ──
    CRITERIA_MAX_AI = 3.5               # rise_probability / 10, capped at 3.5
    CRITERIA_MAX_TECH = 3.5             # golden_cross(1.5) + RSI(1.0) + MACD(1.0)
    CRITERIA_MAX_SENTIMENT = 1.0        # raw average_sentiment_score 범위 0~1

    # ── 공통 가중치 (기본값) ──
    DEFAULT_WEIGHT_AI = Decimal("0.3")
    DEFAULT_WEIGHT_TECH = Decimal("0.4")
    DEFAULT_WEIGHT_SENTIMENT = Decimal("0.3")
