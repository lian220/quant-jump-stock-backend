"""NEWS_SENTIMENT 응답의 rate-limit/일일한도 감지 테스트.

배경(2026-06-11 조사): Alpha Vantage 일일한도(25/day)는 HTTP 200 + 본문
{"Information": "...standard API rate limit is 25 requests per day..."} 로 온다.
기존 코드는 "Note"(분당)만 보고 "Information"(일일)을 놓쳐, 소진된 키를
mark_success 로 오집계하고 티커를 조용히 드롭("No feed data")했다.
→ 200 본문이 한도 메시지인지 판정하는 분류 함수가 두 형태를 모두 잡아야 한다.
"""
from services.sentiment_analysis import SentimentAnalysisService


def test_information_daily_limit_detected_as_rate_limited():
    """일일한도 'Information' 메시지(HTTP 200 본문)를 rate-limit으로 감지한다."""
    data = {
        "Information": (
            "We have detected your API key as ABCD and our standard API rate limit is "
            "25 requests per day. Please subscribe to any of the premium plans at "
            "https://www.alphavantage.co/premium/ to instantly remove all daily rate limits."
        )
    }
    assert SentimentAnalysisService._is_rate_limited_payload(data) is True


def test_note_per_minute_limit_detected_as_rate_limited():
    """기존 분당한도 'Note' 메시지도 계속 감지한다(회귀 방지)."""
    data = {
        "Note": (
            "Thank you for using Alpha Vantage! Our standard API call frequency is "
            "5 calls per minute and 500 calls per day."
        )
    }
    assert SentimentAnalysisService._is_rate_limited_payload(data) is True


def test_normal_feed_payload_not_rate_limited():
    """정상 feed 응답은 rate-limit이 아니다."""
    data = {"items": "50", "feed": [{"title": "NVDA up"}]}
    assert SentimentAnalysisService._is_rate_limited_payload(data) is False


def test_error_message_payload_not_rate_limited():
    """'Error Message'(잘못된 호출 등)는 rate-limit으로 분류하지 않는다(별도 처리 경로)."""
    data = {"Error Message": "Invalid API call. Please retry or visit the documentation."}
    assert SentimentAnalysisService._is_rate_limited_payload(data) is False


def test_empty_payload_not_rate_limited():
    """빈 dict 는 rate-limit 아님."""
    assert SentimentAnalysisService._is_rate_limited_payload({}) is False


# ─────────────────────────────────────────────────────────────────────────────
# 통합: 200 + Information(일일한도) 응답이 mark_success 가 아니라
# mark_rate_limited 로 가고 티커가 저장되지 않아야 한다 (silent drop 회귀 방지)
# ─────────────────────────────────────────────────────────────────────────────
from unittest.mock import MagicMock, patch


def _make_service_with_mock_rotator(num_keys=2):
    with patch("services.sentiment_analysis.AlphaVantageKeyRotator"):
        svc = SentimentAnalysisService()
    rot = MagicMock()
    rot.keys = [f"k{i}" for i in range(num_keys)]
    rot.get_next_key.return_value = "k0"
    rot.should_backoff.return_value = False
    rot.BACKOFF_DELAY = 0
    rot.get_statistics.return_value = {
        "total_calls": num_keys, "success_rate": "0.00%", "rate_limited_count": num_keys,
    }
    svc.key_rotator = rot
    return svc, rot


def test_information_200_marks_rate_limited_not_success_and_skips_save():
    """일일한도(200+Information)면 키를 rate-limited 로 표시하고 재시도, 저장 안 함."""
    svc, rot = _make_service_with_mock_rotator(num_keys=2)

    resp = MagicMock()
    resp.status_code = 200
    resp.json.return_value = {
        "Information": "standard API rate limit is 25 requests per day. premium plans ..."
    }

    db = MagicMock()
    db.sentiment_analysis.find_one.return_value = None  # 미수집 상태 (멱등성 가드 통과)
    with patch("services.sentiment_analysis.PostgreSQL.execute_query",
               return_value=[{"ticker": "NVDA"}]), \
         patch("services.sentiment_analysis.MongoDB.get_db", return_value=db), \
         patch("services.sentiment_analysis.requests.get", return_value=resp):
        results = svc.fetch_and_store_sentiment(start_date="2026-06-09")

    assert rot.mark_rate_limited.called, "소진 키가 rate-limited 로 표시돼야 함"
    assert not rot.mark_success.called, "한도 응답을 success 로 오집계하면 안 됨"
    assert results == [], "feed 없으므로 저장 결과 없어야 함"
    assert not db.sentiment_analysis.update_one.called, "한도 응답으로 저장하면 안 됨"


def test_already_collected_ticker_skipped_no_api_call():
    """당일 이미 수집된 티커는 AV 호출을 건너뛴다 (멱등성 — 다중 트리거 콜 낭비 방지).

    AAA: 당일 데이터 이미 있음 → API 호출 X
    BBB: 없음 → API 호출 O (결측만 재시도)
    """
    svc, rot = _make_service_with_mock_rotator(num_keys=1)

    db = MagicMock()
    # AAA 만 당일 수집 완료(article_count>0), BBB 는 없음
    def _find_one(query):
        return {"article_count": 50} if query.get("ticker") == "AAA" else None
    db.sentiment_analysis.find_one.side_effect = _find_one

    resp = MagicMock()
    resp.status_code = 200
    resp.json.return_value = {
        "feed": [{
            "title": "BBB news",
            "time_published": "20260609T120000",
            "ticker_sentiment": [{
                "ticker": "BBB", "ticker_sentiment_score": "0.3",
                "ticker_sentiment_label": "Bullish", "relevance_score": "0.5",
            }],
        }]
    }

    with patch("services.sentiment_analysis.PostgreSQL.execute_query",
               return_value=[{"ticker": "AAA"}, {"ticker": "BBB"}]), \
         patch("services.sentiment_analysis.MongoDB.get_db", return_value=db), \
         patch("services.sentiment_analysis.requests.get", return_value=resp) as mock_get:
        svc.fetch_and_store_sentiment(start_date="2026-06-09")

    assert mock_get.call_count == 1, "이미 수집된 AAA 는 호출 안 하고 BBB 만 호출해야 함"
    called_ticker = mock_get.call_args.kwargs["params"]["tickers"]
    assert called_ticker == "BBB", f"호출된 티커는 결측인 BBB 여야 함 (got {called_ticker})"


# ─────────────────────────────────────────────────────────────────────────────
# 전역 fail-fast: 모든 키가 일일한도 소진(should_backoff=True)되면, 남은 종목을
# 갈아넣지 않고 배치를 즉시 종료한다. + 일일한도엔 무의미한 60초 백오프를 자지 않는다.
#
# 배경(2026-06-17 비용 사고): 소진 감지가 '종목 단위'라 배치가 안 멈췄다.
# 42종목을 종목마다 60초 백오프로 갈아넣어 처리시간이 600초(Pub/Sub ack deadline)를
# 넘김 → ack 실패 → 메시지 재전송 → 파이프라인 5회 재실행(Vertex AI 5회, Slack 45개).
# ─────────────────────────────────────────────────────────────────────────────
def test_all_keys_exhausted_aborts_batch_without_backoff_sleep():
    """전 키 소진(should_backoff=True) 시 첫 감지에서 배치 종료, 60초 백오프 안 잠."""
    svc, rot = _make_service_with_mock_rotator(num_keys=3)
    rot.should_backoff.return_value = True  # 모든 키가 일일한도 소진 상태
    rot.BACKOFF_DELAY = 60

    resp = MagicMock()
    resp.status_code = 200
    resp.json.return_value = {
        "Information": "standard API rate limit is 25 requests per day. premium plans ..."
    }

    db = MagicMock()
    db.sentiment_analysis.find_one.return_value = None  # 3종목 모두 미수집

    with patch("services.sentiment_analysis.PostgreSQL.execute_query",
               return_value=[{"ticker": "AAA"}, {"ticker": "BBB"}, {"ticker": "CCC"}]), \
         patch("services.sentiment_analysis.MongoDB.get_db", return_value=db), \
         patch("services.sentiment_analysis.requests.get", return_value=resp) as mock_get, \
         patch("time.sleep") as mock_sleep:
        results = svc.fetch_and_store_sentiment(start_date="2026-06-09")

    # 전량 소진을 처음 확인한 직후 배치 종료 → 남은 BBB/CCC 는 갈아넣지 않음
    assert mock_get.call_count == 1, (
        f"전 키 소진 시 첫 감지에서 배치 종료해야 함 (got {mock_get.call_count} calls)")
    # 일일한도(25/day)는 60초 기다려도 안 풀림 → 백오프 sleep 금지
    assert not any(
        (c.args and c.args[0] == 60) for c in mock_sleep.call_args_list
    ), "일일한도 소진 시 60초 백오프를 자면 안 됨"
    assert results == []
