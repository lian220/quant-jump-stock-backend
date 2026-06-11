import logging
import requests
import time
from datetime import datetime, timedelta
from core.database import MongoDB, PostgreSQL
from core.config import settings
from core.api_key_rotator import AlphaVantageKeyRotator
from core.timezone import latest_complete_bar_date_str

logger = logging.getLogger(__name__)

# Alpha Vantage 한도/스로틀 응답 식별 문구 (HTTP 200 본문으로 옴).
# - 일일한도(25/day): {"Information": "...standard API rate limit is 25 requests per day..."}
# - 분당한도(레거시): {"Note": "...standard API call frequency is 5 calls per minute..."}
_RATE_LIMIT_HINTS = (
    "rate limit",
    "requests per day",
    "calls per minute",
    "call frequency",
    "premium plan",
)

class SentimentAnalysisService:
    def __init__(self):
        # 🆕 API 키 로테이터 사용
        self.key_rotator = AlphaVantageKeyRotator()
        self.base_url = "https://www.alphavantage.co/query"

    @staticmethod
    def _is_rate_limited_payload(data: dict) -> bool:
        """HTTP 200 응답 본문이 AV 한도/스로틀 메시지인지 판정.

        AV 는 키 소진 시 429 가 아니라 200 + {"Information"|"Note": "..."} 를 준다.
        'feed' 가 없어 기존 코드는 "No feed data"로 조용히 드롭했는데, 실제로는
        키를 회피(rotate)하고 재시도해야 하는 케이스다.
        """
        for key in ("Information", "Note"):
            msg = data.get(key)
            if msg and any(hint in str(msg).lower() for hint in _RATE_LIMIT_HINTS):
                return True
        return False

    def fetch_and_store_sentiment(self, start_date=None, end_date=None):
        logger.info(f"Starting sentiment analysis... ({start_date} ~ {end_date})")

        # API 키 확인
        if not self.key_rotator.keys:
            logger.warning("⚠️ Alpha Vantage API 키가 없습니다. 뉴스 감정 분석을 건너뜁니다.")
            return []

        db = MongoDB.get_db()

        # 1. Get Tickers from PostgreSQL
        try:
            active_stocks = PostgreSQL.execute_query(
                "SELECT ticker FROM stocks WHERE is_active = TRUE",
                fetch_all=True
            )
            tickers = [s["ticker"] for s in active_stocks if s.get("ticker")]
        except Exception as e:
            logger.error(f"Failed to fetch active stocks from PostgreSQL: {e}")
            return []

        if not tickers:
            logger.warning("No tickers found for sentiment analysis.")
            return []
            
        # 2. Date Setup
        if not start_date:
            start_date = latest_complete_bar_date_str()
        
        start_date_dt = datetime.strptime(start_date, '%Y-%m-%d')
        time_from = (start_date_dt - timedelta(days=3)).strftime("%Y%m%dT0000")

        results = []

        for ticker in tickers:
            logger.debug(f"Fetching sentiment for {ticker}...")

            # 🆕 최대 3번 재시도 (키 로테이션)
            max_retries = min(3, len(self.key_rotator.keys))
            success = False
            all_keys_rate_limited_count = 0  # 모든 키 Rate Limit 카운터
            MAX_ALL_KEYS_RATE_LIMITED = 3  # 모든 키 Rate Limit 시 최대 재시도

            for attempt in range(max_retries):
                # 다음 API 키 가져오기
                current_key = self.key_rotator.get_next_key()

                params = {
                    "function": "NEWS_SENTIMENT",
                    "time_from": time_from,
                    "limit": 100,
                    "tickers": ticker,
                    "apikey": current_key
                }

                try:
                    response = requests.get(self.base_url, params=params, timeout=30)

                    # Rate Limit 감지 (429 또는 특정 메시지)
                    if response.status_code == 429:
                        logger.warning(f"Rate Limit (429) for {ticker}, attempt {attempt + 1}/{max_retries}")
                        self.key_rotator.mark_rate_limited(current_key)

                        # 모든 키가 Rate Limit 걸렸는지 확인
                        if self.key_rotator.should_backoff():
                            all_keys_rate_limited_count += 1
                            if all_keys_rate_limited_count >= MAX_ALL_KEYS_RATE_LIMITED:
                                logger.error(
                                    f"⛔ {ticker}: 모든 API 키 Rate Limit, "
                                    f"{MAX_ALL_KEYS_RATE_LIMITED}회 재시도 실패 - 스킵"
                                )
                                break
                            logger.warning(
                                f"⏳ 모든 키 Rate Limit ({all_keys_rate_limited_count}/{MAX_ALL_KEYS_RATE_LIMITED}), "
                                f"{self.key_rotator.BACKOFF_DELAY}초 대기..."
                            )
                            time.sleep(self.key_rotator.BACKOFF_DELAY)
                        continue

                    if response.status_code != 200:
                        logger.warning(f"API error for {ticker}: {response.status_code}")
                        self.key_rotator.mark_failure(current_key)
                        continue

                    # HTTP 200 이어도 본문이 한도 메시지(Information/Note)면 성공 아님.
                    # AV 는 키 소진 시 429 가 아니라 200 + Information 을 주므로 여기서 잡아
                    # 소진 키를 회피(rotate)하고 다음 키로 재시도한다. (silent drop 방지)
                    try:
                        payload = response.json()
                    except ValueError:
                        payload = {}
                    if self._is_rate_limited_payload(payload):
                        logger.warning(
                            f"한도 본문 감지 for {ticker} "
                            f"(attempt {attempt + 1}/{max_retries}) → 키 회피 후 재시도"
                        )
                        self.key_rotator.mark_rate_limited(current_key)
                        if self.key_rotator.should_backoff():
                            all_keys_rate_limited_count += 1
                            if all_keys_rate_limited_count >= MAX_ALL_KEYS_RATE_LIMITED:
                                logger.error(
                                    f"⛔ {ticker}: 모든 API 키 일일한도 소진 "
                                    f"({MAX_ALL_KEYS_RATE_LIMITED}회) - 스킵"
                                )
                                break
                            time.sleep(self.key_rotator.BACKOFF_DELAY)
                        continue

                    # 성공
                    self.key_rotator.mark_success(current_key)
                    success = True
                    break

                except requests.exceptions.Timeout:
                    logger.warning(f"Timeout for {ticker}, attempt {attempt + 1}/{max_retries}")
                    self.key_rotator.mark_failure(current_key)
                    continue
                except Exception as e:
                    logger.error(f"Request error for {ticker}: {e}")
                    self.key_rotator.mark_failure(current_key)
                    continue

            if not success:
                logger.error(f"Failed to fetch sentiment for {ticker} after {max_retries} attempts")
                continue

            # 응답 데이터 처리
            try:
                data = response.json()

                # Alpha Vantage 에러 메시지 체크
                if "Error Message" in data:
                    logger.warning(f"API Error for {ticker}: {data['Error Message']}")
                    continue

                if "Note" in data and "rate limit" in data["Note"].lower():
                    logger.warning(f"Rate Limit message for {ticker}: {data['Note']}")
                    self.key_rotator.wait_if_needed(current_key, is_rate_limited=True)
                    continue

                if "feed" not in data:
                    logger.warning(f"No feed data for {ticker}")
                    continue

                # Calculate average sentiment AND collect original articles
                sentiment_scores = []
                articles = []  # 원본 기사 목록

                for article in data["feed"]:
                    for ticker_sentiment in article.get("ticker_sentiment", []):
                        if ticker_sentiment.get("ticker") == ticker:
                            score = float(ticker_sentiment.get("ticker_sentiment_score", 0))
                            sentiment_scores.append(score)

                            # 원본 기사 정보 추출
                            published_time = article.get("time_published", "")
                            published_at = None
                            if published_time:
                                try:
                                    published_at = datetime.strptime(published_time, "%Y%m%dT%H%M%S")
                                except (ValueError, OverflowError) as e:
                                    logger.debug(f"날짜 파싱 실패 ({published_time}): {e}")
                                    published_at = datetime.utcnow()
                            else:
                                published_at = datetime.utcnow()

                            article_info = {
                                "published_at": published_at,
                                "title": article.get("title", "")[:200],
                                "summary": article.get("summary", "")[:500],
                                "url": article.get("url", ""),
                                "source": article.get("source", "unknown"),
                                "image_url": article.get("banner_image"),
                                "sentiment_score": score,
                                "sentiment_label": ticker_sentiment.get("ticker_sentiment_label", "neutral"),
                                "relevance_score": float(ticker_sentiment.get("relevance_score", 0))
                            }
                            articles.append(article_info)

                if sentiment_scores:
                    avg_score = sum(sentiment_scores) / len(sentiment_scores)
                    article_count = len(sentiment_scores)

                    # sentiment_analysis 컬렉션에 요약 + 원본 기사 모두 저장
                    doc = {
                        "ticker": ticker,
                        "date": start_date,
                        "average_sentiment_score": avg_score,
                        "article_count": article_count,
                        "articles": articles,  # 원본 기사 배열 추가
                        "updated_at": datetime.utcnow()
                    }

                    db.sentiment_analysis.update_one(
                        {"ticker": ticker, "date": start_date},
                        {"$set": doc},
                        upsert=True
                    )

                    results.append(doc)
                    logger.debug(f"Saved sentiment for {ticker}: Score={avg_score:.2f}, Articles={article_count}")

                # 🆕 Rate Limit 준수 (5 calls/min → 12초 대기)
                self.key_rotator.wait_if_needed(current_key, is_rate_limited=False)

            except Exception as e:
                logger.error(f"Error processing sentiment for {ticker}: {e}")

        # 🆕 통계 로깅
        stats = self.key_rotator.get_statistics()
        logger.info(f"📊 API Key 사용 통계: {stats['total_calls']}회 호출, "
                   f"성공률: {stats['success_rate']}, "
                   f"Rate Limit: {stats['rate_limited_count']}회")

        return results
