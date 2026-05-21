"""buy_criteria 가 ScoringPolicy public API 만 사용하여 현행과 동일 결과."""
from decimal import Decimal
from services.buy_criteria import BuyCriteria
from domain.recommendation.scoring_policy import ScoringPolicy


def test_calculate_uses_policy_public_api_only():
    p = ScoringPolicy.load_default()
    bc = BuyCriteria(policy=p)
    out = bc.calculate_composite_score(
        indicators={"golden_cross": True, "rsi": 50, "macd_buy_signal": True},
        ai_score=10.0,           # 이미 normalized 0~10
        sentiment_score=5.0,     # 이미 normalized 0~10
        has_ai=True,
        has_sentiment=True,
        has_tech=True,
    )
    # 현행: composite = 0.3*10 + 0.4*3.5 + 0.3*5 = 5.9
    assert out["composite_score"] == 5.9
    assert out["max_possible"] == 7.4    # 현행 buy_criteria 2.85 → 7.4 통일
    assert abs(out["confidence"] - 0.797) < 0.01
    assert out["missing_axes"] == []


def test_buy_criteria_does_not_call_private_policy_methods():
    """리뷰 #6: BuyCriteria 가 _calc_* 같은 private 호출 안 함."""
    import inspect
    from services import buy_criteria as bc_module
    source = inspect.getsource(bc_module)
    assert "_calc_tech_score" not in source
    assert "_compose" not in source
    assert "_calc_sentiment_score" not in source
    assert "_calc_ai_score" not in source


def test_filter_candidates_uses_ssot_scale_directly():
    """comprehensive_report 가 normalize 한 0-10 스케일 ai/sent 를 그대로 사용 (rescale 어댑터 없음)."""
    from services.buy_criteria import BuyCriteria
    from domain.recommendation.scoring_policy import ScoringPolicy
    policy = ScoringPolicy.load_default()
    bc = BuyCriteria(policy=policy)
    # rescale 메서드 부재 확인
    assert not hasattr(bc, "_rescale_legacy_inputs"), "rescale 어댑터는 제거되어야 함"
