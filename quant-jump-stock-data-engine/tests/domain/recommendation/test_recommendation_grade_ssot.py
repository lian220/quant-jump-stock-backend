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


def test_grade_priority_still_static():
    """priority 는 정적 정렬 키 — spec 외 (운영 의존 없음)."""
    grades = sorted([RecommendationGrade.NONE, RecommendationGrade.STRONG, RecommendationGrade.WATCH], key=lambda g: g.priority)
    assert grades == [RecommendationGrade.STRONG, RecommendationGrade.WATCH, RecommendationGrade.NONE]
