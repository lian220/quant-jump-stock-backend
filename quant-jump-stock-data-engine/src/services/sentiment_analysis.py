import logging
import requests
import time
from datetime import datetime, timedelta
from src.core.database import MongoDB
from src.core.config import settings

logger = logging.getLogger(__name__)

class SentimentAnalysisService:
    def __init__(self):
        self.api_key = settings.ALPHA_VANTAGE_API_KEY
        self.base_url = "https://www.alphavantage.co/query"

    def fetch_and_store_sentiment(self, start_date=None, end_date=None):
        logger.info(f"Starting sentiment analysis... ({start_date} ~ {end_date})")
        db = MongoDB.get_db()
        
        # 1. Get Tickers (Union of Active Stocks and Holdings)
        # For MVP, just get active stocks
        try:
            active_stocks = list(db.stocks.find({"is_active": True}))
            tickers = [s["ticker"] for s in active_stocks if s.get("ticker")]
        except Exception as e:
            logger.error(f"Failed to fetch active stocks: {e}")
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
                    
                # Calculate simple average sentiment
                sentiment_scores = []
                for article in data["feed"]:
                    for ticker_sentiment in article.get("ticker_sentiment", []):
                        if ticker_sentiment.get("ticker") == ticker:
                            score = float(ticker_sentiment.get("ticker_sentiment_score", 0))
                            sentiment_scores.append(score)
                
                if sentiment_scores:
                    avg_score = sum(sentiment_scores) / len(sentiment_scores)
                    article_count = len(sentiment_scores)
                    
                    doc = {
                        "ticker": ticker,
                        "date": start_date,
                        "average_sentiment_score": avg_score,
                        "article_count": article_count,
                        "updated_at": datetime.utcnow()
                    }
                    
                    db.sentiment_analysis.update_one(
                        {"ticker": ticker, "date": start_date},
                        {"$set": doc},
                        upsert=True
                    )
                    
                    results.append(doc)
                    logger.info(f"Saved sentiment for {ticker}: Score={avg_score:.2f}, Count={article_count}")
                
                # Respect rate limits (Alpha Vantage free tier: 5 calls/min)
                # Assuming key allows more or we sleep more. conservative sleep.
                time.sleep(12) 
                
            except Exception as e:
                logger.error(f"Error fetching sentiment for {ticker}: {e}")
                
        return results
