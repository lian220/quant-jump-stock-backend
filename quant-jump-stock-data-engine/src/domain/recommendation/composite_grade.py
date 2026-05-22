"""
RecommendationGrade - Slack 리포트 표시용 추천 등급 (Domain Layer).

PR 1+2 가 산식 SSoT 를 `ScoringPolicy` + `scoring_spec.yaml` 로 통합 후,
sync_service 는 `Score.grade` (문자열 "S"/"A"/"B"/"C"/"D") 를 직접 PG 에 저장.
별도 `CompositeGrade` Python enum 은 불필요 → cleanup PR 에서 제거 (2026-05-22).
"""
from enum import Enum
from typing import Any, Dict


def _spec_label(key: str) -> str:
    """recommendation_labels[key].label — ScoringPolicy lazy 로드."""
    # 순환 import 방지: 함수 내부 import
    from domain.recommendation.scoring_policy import ScoringPolicy
    return ScoringPolicy.load_default().label_metadata(key)["label"]


def _spec_emoji(key: str) -> str:
    from domain.recommendation.scoring_policy import ScoringPolicy
    return ScoringPolicy.load_default().label_metadata(key)["emoji"]


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
