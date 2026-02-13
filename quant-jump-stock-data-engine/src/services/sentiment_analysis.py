import logging
import requests
import time
from datetime import datetime, timedelta
from core.database import MongoDB, PostgreSQL
from core.config import settings

logger = logging.getLogger(__name__)

class SentimentAnalysisService:
    def __init__(self):
        self.api_key = settings.ALPHA_VANTAGE_API_KEY
        self.base_url = "https://www.alphavantage.co/query"

    def fetch_and_store_sentiment(self, start_date=None, end_date=None):
        logger.info(f"Starting sentiment analysis... ({start_date} ~ {end_date})")
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
            start_date = datetime.now().strftime('%Y-%m-%d')
        
        start_date_dt = datetime.strptime(start_date, '%Y-%m-%d')
        time_from = (start_date_dt - timedelta(days=3)).strftime("%Y%m%dT0000")
        
        params = {
            "function": "NEWS_SENTIMENT",
            "time_from": time_from,
            "limit": 100,
            "apikey": self.api_key
        }
        
        results = []
        
        for ticker in tickers:
            logger.info(f"Fetching sentiment for {ticker}...")
            params["tickers"] = ticker
            
            try:
                response = requests.get(self.base_url, params=params, timeout=30)
                if response.status_code != 200:
                    logger.warning(f"Alpha Vantage API error for {ticker}: {response.status_code}")
                    continue
                    
                data = response.json()
                if "feed" not in data:
                    logger.warning(f"No feed data for {ticker}")
                    continue
                    
                # Calculate simple average sentiment AND store original articles
                sentiment_scores = []
                news_articles = []  # 원본 기사 저장용

                for article in data["feed"]:
                    for ticker_sentiment in article.get("ticker_sentiment", []):
                        if ticker_sentiment.get("ticker") == ticker:
                            score = float(ticker_sentiment.get("ticker_sentiment_score", 0))
                            sentiment_scores.append(score)

                            # 🆕 원본 기사 정보 추출
                            published_time = article.get("time_published", "")
                            # Alpha Vantage 포맷: "20260213T093000" → "2026-02-13T09:30:00Z"
                            published_at = None
                            if published_time:
                                try:
                                    published_at = datetime.strptime(published_time, "%Y%m%dT%H%M%S")
                                except:
                                    published_at = datetime.utcnow()
                            else:
                                published_at = datetime.utcnow()

                            news_doc = {
                                "ticker": ticker,
                                "date": start_date,
                                "published_at": published_at,
                                "title": article.get("title", "")[:200],  # 제목 200자 제한
                                "summary": article.get("summary", "")[:500],  # 요약 500자 제한
                                "url": article.get("url", ""),
                                "source": article.get("source", "unknown"),
                                "image_url": article.get("banner_image"),
                                "sentiment": {
                                    "score": score,
                                    "label": ticker_sentiment.get("ticker_sentiment_label", "neutral"),
                                    "relevance_score": float(ticker_sentiment.get("relevance_score", 0))
                                },
                                "language": "en",
                                "created_at": datetime.utcnow(),
                                "updated_at": datetime.utcnow()
                            }
                            news_articles.append(news_doc)

                if sentiment_scores:
                    avg_score = sum(sentiment_scores) / len(sentiment_scores)
                    article_count = len(sentiment_scores)

                    # 1️⃣ 기존: 요약 저장 (sentiment_analysis 컬렉션)
                    summary_doc = {
                        "ticker": ticker,
                        "date": start_date,
                        "average_sentiment_score": avg_score,
                        "article_count": article_count,
                        "updated_at": datetime.utcnow()
                    }

                    db.sentiment_analysis.update_one(
                        {"ticker": ticker, "date": start_date},
                        {"$set": summary_doc},
                        upsert=True
                    )

                    # 2️⃣ 신규: 원본 기사 저장 (news_sentiment 컬렉션)
                    if news_articles:
                        for news_doc in news_articles:
                            db.news_sentiment.update_one(
                                {
                                    "ticker": news_doc["ticker"],
                                    "url": news_doc["url"]  # URL로 중복 체크
                                },
                                {"$set": news_doc},
                                upsert=True
                            )
                        logger.info(f"Saved {len(news_articles)} news articles for {ticker}")

                    results.append(summary_doc)
                    logger.info(f"Saved sentiment for {ticker}: Score={avg_score:.2f}, Count={article_count}")
                
                # Respect rate limits (Alpha Vantage free tier: 5 calls/min)
                # Assuming key allows more or we sleep more. conservative sleep.
                time.sleep(12) 
                
            except Exception as e:
                logger.error(f"Error fetching sentiment for {ticker}: {e}")
                
        return results
