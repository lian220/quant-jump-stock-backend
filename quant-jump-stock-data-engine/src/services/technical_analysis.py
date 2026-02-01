import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import logging
from src.core.database import MongoDB

logger = logging.getLogger(__name__)

class TechnicalAnalysisService:
    def __init__(self):
        self.lookback_days = 180

    def calculate_sma(self, series, period):
        return series.rolling(window=period, min_periods=period).mean()

    def calculate_rsi(self, series, period=14):
        delta = series.diff()
        gain = (delta.where(delta > 0, 0)).rolling(window=period, min_periods=period).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(window=period, min_periods=period).mean()
        epsilon = 1e-10
        rs = gain / (loss + epsilon)
        rs = rs.replace([np.inf, -np.inf], np.nan)
        rsi = 100 - (100 / (1 + rs))
        return rsi.clip(0, 100)

    def calculate_macd(self, series, short_period=12, long_period=26, signal_period=9):
        short_ema = series.ewm(span=short_period, adjust=False).mean()
        long_ema = series.ewm(span=long_period, adjust=False).mean()
        macd = short_ema - long_ema
        signal = macd.ewm(span=signal_period, adjust=False).mean()
        return macd, signal

    def analyze_stocks(self, target_date=None):
        logger.info(f"Starting technical analysis (target_date={target_date})...")
        db = MongoDB.get_db()

        # Get active stocks
        stock_names = []
        try:
            active_stocks = list(db.stocks.find({"is_active": True}))
            stock_names = [s["stock_name"] for s in active_stocks if s.get("stock_name")]
        except Exception as e:
            logger.error(f"Failed to fetch active stocks: {e}")
            return []

        if not stock_names:
            logger.warning("No active stocks found for analysis.")
            return []

        # Determine date range based on target_date
        if target_date:
            # Parse target_date and fetch lookback_days before it
            target_dt = datetime.strptime(target_date, "%Y-%m-%d")
            start_dt = target_dt - timedelta(days=self.lookback_days)
            start_date_str = start_dt.strftime("%Y-%m-%d")
            end_date_str = target_date
            analysis_date = target_date
        else:
            # No target_date specified, use current date
            end_dt = datetime.now()
            start_dt = end_dt - timedelta(days=self.lookback_days)
            start_date_str = start_dt.strftime("%Y-%m-%d")
            end_date_str = end_dt.strftime("%Y-%m-%d")
            analysis_date = end_date_str

        # Fetch daily data
        try:
            daily_data = list(db.daily_stock_data.find({
                "date": {"$gte": start_date_str, "$lte": end_date_str}
            }).sort("date", 1))
            
            if not daily_data:
                logger.warning("No daily stock data found.")
                return []

            # Construct DataFrame
            data_dict = {}
            for doc in daily_data:
                date = doc["date"]
                stocks_data = doc.get("stocks", {})
                
                # Active stocks logic needs ticker mapping. 
                # For simplicity in migration, assuming stocks_data uses Ticker as key.
                # But we need Stock Name -> Ticker mapping or just iterate what's in DB.
                # Let's rely on what's available in stocks_data.
                for ticker, val in stocks_data.items():
                    price = val if isinstance(val, (int, float)) else val.get("close_price")
                    if price:
                        if ticker not in data_dict:
                            data_dict[ticker] = {}
                        data_dict[ticker][date] = float(price)
            
            # Map Ticker to Stock Name for reporting
            ticker_to_name = {s["ticker"]: s["stock_name"] for s in active_stocks if s.get("ticker")}
            
            recommendations = []
            
            for ticker, dates_prices in data_dict.items():
                if len(dates_prices) < 50:
                    continue
                    
                df = pd.DataFrame.from_dict(dates_prices, orient='index', columns=['close'])
                df.index = pd.to_datetime(df.index)
                df.sort_index(inplace=True)
                
                # Fill missing
                df = df.ffill().bfill()
                
                # Setup indicators
                df['sma20'] = self.calculate_sma(df['close'], 20)
                df['sma50'] = self.calculate_sma(df['close'], 50)
                df['rsi'] = self.calculate_rsi(df['close'])
                df['macd'], df['signal'] = self.calculate_macd(df['close'])

                # Use target_date for analysis (not the latest date)
                try:
                    target_dt = pd.to_datetime(analysis_date)
                    if target_dt not in df.index:
                        logger.warning(f"Target date {analysis_date} not found for {ticker}, skipping")
                        continue
                    latest_date = target_dt
                    latest_row = df.loc[latest_date]
                except Exception as e:
                    logger.error(f"Error accessing target date {analysis_date} for {ticker}: {e}")
                    continue
                
                golden_cross = latest_row['sma20'] > latest_row['sma50']
                macd_buy = latest_row['macd'] > latest_row['signal']
                is_recommended = golden_cross and (latest_row['rsi'] < 50) and macd_buy
                
                rec_data = {
                    "date": latest_date.strftime("%Y-%m-%d"),
                    "ticker": ticker,
                    "stock_name": ticker_to_name.get(ticker, ticker),
                    "technical_indicators": {
                        "sma20": latest_row['sma20'],
                        "sma50": latest_row['sma50'],
                        "rsi": latest_row['rsi'],
                        "macd": latest_row['macd'],
                        "signal": latest_row['signal'],
                        "golden_cross": bool(golden_cross),
                        "macd_buy_signal": bool(macd_buy)
                    },
                    "is_recommended": bool(is_recommended),
                    "updated_at": datetime.utcnow()
                }
                
                # Save to MongoDB (stock_recommendations)
                db.stock_recommendations.update_one(
                    {"ticker": ticker, "date": rec_data["date"]},
                    {"$set": rec_data},
                    upsert=True
                )
                
                if is_recommended:
                    recommendations.append(rec_data)
                    
            logger.info(f"Analysis complete. {len(recommendations)} stocks recommended.")
            return recommendations

        except Exception as e:
            logger.error(f"Analysis failed: {e}")
            import traceback
            logger.error(traceback.format_exc())
            return []
