"""
RecommendationGrade - Slack 리포트 표시용 추천 등급 (Domain Layer).

현재 enum 의 정적 책임은 priority(정렬 키)뿐이다 — label/emoji/판정 임계는 전부
`ScoringPolicy`(scoring_spec.yaml SSoT) lazy 위임. 호출처는 slack_notifier(표시 순회)와
buy_criteria(NONE 비교/정렬). 완전 제거는 두 호출처 정리와 함께 별도 cleanup 으로 수행.
"""
from decimal import Decimal
from enum import Enum
from typing import Any, Dict


def _policy():
    """ScoringPolicy lazy 로드 — 순환 import 방지를 위한 단일 wrapper."""
    from domain.recommendation.scoring_policy import ScoringPolicy
    return ScoringPolicy.load_default()


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
        return _policy().label_metadata(self.name)["label"]

    @property
    def emoji(self) -> str:
        return _policy().label_metadata(self.name)["emoji"]

    @staticmethod
    def from_scores(scores: Dict[str, Any]) -> "RecommendationGrade":
        """
        confidence 기반 추천 등급 결정.

        임계 SSoT = scoring_spec.yaml.recommendation_labels — ScoringPolicy 위임
        (ADR 0006 §2.8: 점수 기준 하드코딩 금지. 과거 0.65/0.45/0.30 하드코딩이
        yaml 과 중복 정의되어 드리프트하던 결함 제거, 2026-06-10).
        """
        key = _policy().label_from_confidence_and_signals(
            Decimal(str(scores.get("confidence", 0))),
            scores.get("tech_signals", 0),
        )
        return RecommendationGrade[key]
