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
    with patch("services.sentiment_analysis.PostgreSQL.execute_query",
               return_value=[{"ticker": "NVDA"}]), \
         patch("services.sentiment_analysis.MongoDB.get_db", return_value=db), \
         patch("services.sentiment_analysis.requests.get", return_value=resp):
        results = svc.fetch_and_store_sentiment(start_date="2026-06-09")

    assert rot.mark_rate_limited.called, "소진 키가 rate-limited 로 표시돼야 함"
    assert not rot.mark_success.called, "한도 응답을 success 로 오집계하면 안 됨"
    assert results == [], "feed 없으므로 저장 결과 없어야 함"
    assert not db.sentiment_analysis.update_one.called, "한도 응답으로 저장하면 안 됨"
