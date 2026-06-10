"""RecommendationGrade enum 의 label/emoji 가 spec SSoT 에서 lazy 로드 검증 (PR 2)."""
from domain.recommendation.composite_grade import RecommendationGrade


def test_grade_label_from_spec():
    """enum 의 hard-coded label 이 아닌 spec 의 label 반환."""
    assert RecommendationGrade.STRONG.label == "강력 추천"
    assert RecommendationGrade.RECOMMEND.label == "추천"
    assert RecommendationGrade.WATCH.label == "관심 종목"
    assert RecommendationGrade.NONE.label == "추천 없음"


def test_grade_emoji_from_spec():
    assert RecommendationGrade.STRONG.emoji == "🟢"
    assert RecommendationGrade.RECOMMEND.emoji == "🟡"
    assert RecommendationGrade.WATCH.emoji == "🟠"
    assert RecommendationGrade.NONE.emoji == "⚪"


def test_from_scores_thresholds_from_spec():
    """from_scores 라벨 임계는 yaml SSoT — 하드코딩 금지 (ADR 0006 §2.8).

    재보정(2026-06-10, 패널 검증): 등급 경계 정렬 STRONG 0.77(A)/RECOMMEND 0.68(B)/WATCH 0.58(C).
    composite 75 = grade B 인데 라벨 "강력 추천"이던 비일관 해소.
    min_tech_signals 게이트 제거 — tech 는 composite 50% weight 로 이미 반영 (이중 반영 금지).
    """
    assert RecommendationGrade.from_scores({"confidence": 0.77, "tech_signals": 3}) == RecommendationGrade.STRONG
    assert RecommendationGrade.from_scores({"confidence": 0.75, "tech_signals": 3}) == RecommendationGrade.RECOMMEND
    assert RecommendationGrade.from_scores({"confidence": 0.68, "tech_signals": 2}) == RecommendationGrade.RECOMMEND
    assert RecommendationGrade.from_scores({"confidence": 0.60, "tech_signals": 2}) == RecommendationGrade.WATCH
    assert RecommendationGrade.from_scores({"confidence": 0.58, "tech_signals": 1}) == RecommendationGrade.WATCH
    assert RecommendationGrade.from_scores({"confidence": 0.50, "tech_signals": 3}) == RecommendationGrade.NONE
    # 라벨은 confidence 순수 함수 — 신호 수는 게이트가 아님 (grade 와 동일 임계 공유)
    assert RecommendationGrade.from_scores({"confidence": 0.90, "tech_signals": 0}) == RecommendationGrade.STRONG


def test_grade_priority_still_static():
    """priority 는 정적 정렬 키 — spec 외 (운영 의존 없음)."""
    grades = sorted([RecommendationGrade.NONE, RecommendationGrade.STRONG, RecommendationGrade.WATCH], key=lambda g: g.priority)
    assert grades == [RecommendationGrade.STRONG, RecommendationGrade.WATCH, RecommendationGrade.NONE]
