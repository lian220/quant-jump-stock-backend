"""sync_service end-to-end integration — mongo fixture 기반 (Task 3.2).

목적: mongo 데이터 → RecommendationSyncService._merge_analysis_data → Score 객체 생성까지
관통 검증. PR 1 후 회귀 가드 — pipeline 어느 한 부분이라도 깨지면 즉시 잡힘.

mocking 전략:
- MongoDB.get_db() 를 MagicMock 으로 patch (실제 mongo 연결 없음)
- 각 collection (.find / .find_one) 에 작은 in-memory fixture 주입
- PostgreSQL 저장 경로는 _save_to_postgresql 만 담당. 본 테스트는
  순수 merge 메서드 _merge_analysis_data 를 통해 PG side-effect 없이 검증.

SSoT scoring path 를 mock 하지 않는다 (ScoringPolicy 호출 그대로 실행).
"""
from decimal import Decimal
from unittest.mock import MagicMock, patch

import pytest

from domain.recommendation.score import Score
from domain.recommendation.scoring_policy import ScoringPolicy


TARGET_DATE = "2026-05-15"
TICKER = "APP"


def _build_mongo_fixture() -> MagicMock:
    """APP 종목 1건짜리 in-memory mongo mock (date string 통일 후 포맷).

    - daily_stock_data: 기준가 (close)
    - stock_recommendations: 기술적 지표 (golden_cross, rsi, macd_buy_signal)
    - stock_analysis_results: AI 예측 (rise_probability 정규화된 0~1, predicted_future_price)
    - stock_predictions: fallback (없어도 됨, 빈 리스트)
    - sentiment_analysis: 감정 score (-1~+1)
    """
    db = MagicMock()

    # daily_stock_data: _fetch_current_prices 가 find_one 사용
    # 실제 코드: stock_data["close"] (close_price 아님 — 2026-05-20 통일 직후 확인)
    daily_doc = {
        "date": TARGET_DATE,
        "stocks": {
            TICKER: {
                "open": 488.0,
                "high": 495.0,
                "low": 487.5,
                "close": 492.38,
                "volume": 8_500_000,
                "close_price": 492.38,
                "info": {"trailingPE": 28.5, "marketCap": 156_000_000_000},
            }
        },
    }
    db.daily_stock_data.find_one.return_value = daily_doc

    # stock_recommendations (technical indicators)
    rec_doc = {
        "ticker": TICKER,
        "date": TARGET_DATE,
        "stock_name": "AppLovin Corporation",
        "is_recommended": True,
        "technical_indicators": {
            "sma20": 470,
            "sma50": 449,
            "rsi": 54.86,        # < 70 → 1.0 점
            "macd": 12.31,
            "signal": 10.21,
            "golden_cross": True,         # 1.5 점
            "macd_buy_signal": True,      # 1.0 점
        },
    }
    db.stock_recommendations.find.return_value = [rec_doc]

    # stock_analysis_results (AI 예측 — rise_probability 는 raw % (e.g. 14 = +14%))
    # _fetch_stock_analysis_results 가 0.5 + rise_pct/40 = 0.85 로 정규화
    ar_doc = {
        "ticker": TICKER,
        "date": TARGET_DATE,
        "predictions": {
            "rise_probability": 14.0,            # +14% → normalized 0.85
            "predicted_future_price": 669.10,
        },
        "metrics": {"accuracy": 84.19},
        "recommendation": "STRONG BUY",
    }
    db.stock_analysis_results.find.return_value = [ar_doc]

    # stock_predictions (fallback path — 본 케이스 미사용이지만 빈 리스트로 안전화)
    db.stock_predictions.find.return_value = []

    # sentiment_analysis (-1~+1)
    sent_doc = {
        "ticker": TICKER,
        "date": TARGET_DATE,
        "average_sentiment_score": 0.4,           # → sentiment_score 4.0
        "article_count": 12,
    }
    db.sentiment_analysis.find.return_value = [sent_doc]

    return db


@pytest.fixture
def sync_service_with_mongo_fixture():
    """RecommendationSyncService 인스턴스 + mongo fixture 주입.

    MongoDB.get_db 를 patch 한 상태에서 생성하여 실제 mongo 연결을 회피한다.
    """
    mongo_mock = _build_mongo_fixture()
    with patch("application.recommendation.sync_service.MongoDB") as MongoCls:
        MongoCls.get_db.return_value = mongo_mock
        # 지연 import: patch 이후에 import 해야 안전 (sync_service 모듈은 이미 로드돼있을 수 있지만
        # __init__ 내 MongoDB.get_db() 호출이 patched cls 를 본다)
        from application.recommendation.sync_service import RecommendationSyncService

        svc = RecommendationSyncService()
        yield svc, mongo_mock


def test_sync_pipeline_e2e_produces_valid_score(sync_service_with_mongo_fixture):
    """mongo fixture → fetch → _merge_analysis_data → Score 객체.

    검증 포인트:
    1. merged 결과에 우리가 넣은 ticker 가 등장
    2. "score" 키가 Score frozen dataclass 인스턴스
    3. composite_score / grade / composite_max 가 ScoringPolicy 와 동일
    4. tech_signal 3개 (golden_cross + rsi<70 + macd) 모두 인식
    """
    svc, _mongo = sync_service_with_mongo_fixture

    # End-to-end 의 sync_latest_recommendations 는 PostgreSQL 저장까지 동반하므로,
    # PG side-effect 없는 순수 merge 경로를 직접 실행 (SSoT scoring 은 그대로 동작).
    ai = svc._fetch_ai_predictions(TARGET_DATE)
    ai_results = svc._fetch_stock_analysis_results(TARGET_DATE)
    ai = svc._merge_ai_sources(ai, ai_results)
    sentiment = svc._fetch_sentiment_analysis(TARGET_DATE)
    tech = svc._fetch_technical_analysis(TARGET_DATE)
    prices = svc._fetch_current_prices(TARGET_DATE)

    merged = svc._merge_analysis_data(ai, sentiment, tech, prices)

    # 1. ticker 존재
    assert len(merged) == 1, f"expected 1 merged row, got {len(merged)}: {merged}"
    row = merged[0]
    assert row["ticker"] == TICKER

    # 2. Score 객체
    score = row["score"]
    assert isinstance(score, Score), f"expected Score, got {type(score).__name__}"

    # 3. SSoT 산식과 동일한 composite 인지 (회귀 가드의 핵심)
    policy = ScoringPolicy.load_default()
    expected_ai = policy.ai_score_from_normalized(Decimal("0.85"))
    expected_tech = policy.tech_score_from_indicators({
        "golden_cross": True, "rsi": 54.86, "macd_buy_signal": True
    })
    expected_sent = policy.sentiment_score_from_raw(Decimal("0.4"))
    expected = policy.compose_components(
        ai_score=expected_ai,
        tech_score=expected_tech,
        sentiment_score=expected_sent,
        has_ai=True, has_tech=True, has_sentiment=True,
        tech_signal_count=3,
    )

    assert score.composite_score == expected.composite_score, (
        f"composite mismatch: pipeline={score.composite_score} vs policy={expected.composite_score}"
    )
    assert score.grade == expected.grade
    assert score.composite_max == expected.composite_max == Decimal("7.40")
    assert score.recommendation_label == expected.recommendation_label

    # 4. tech signal 3개 인식 (sma cross + rsi<threshold + macd buy)
    assert row["tech_signals_count"] == 3
    assert row["composite_score"] == float(expected.composite_score)


def test_sync_pipeline_missing_sentiment_still_scores(sync_service_with_mongo_fixture):
    """sentiment 데이터 누락 시에도 ScoringPolicy 가 missing_axes 로 처리.

    회귀 가드: 부분 데이터 케이스에서도 Score 가 정상 생성되고 missing_axes 채워지는지.
    """
    svc, mongo = sync_service_with_mongo_fixture
    # sentiment 만 비우기
    mongo.sentiment_analysis.find.return_value = []

    ai = svc._fetch_ai_predictions(TARGET_DATE)
    ai_results = svc._fetch_stock_analysis_results(TARGET_DATE)
    ai = svc._merge_ai_sources(ai, ai_results)
    sentiment = svc._fetch_sentiment_analysis(TARGET_DATE)
    tech = svc._fetch_technical_analysis(TARGET_DATE)
    prices = svc._fetch_current_prices(TARGET_DATE)

    merged = svc._merge_analysis_data(ai, sentiment, tech, prices)
    assert len(merged) == 1
    score = merged[0]["score"]

    assert isinstance(score, Score)
    assert "sentiment" in score.missing_axes
    # ai + tech 만 기여: 0.3*7.0 + 0.4*3.5 = 2.1 + 1.4 = 3.5
    assert score.composite_score == Decimal("3.50")
    assert score.grade == "B"
