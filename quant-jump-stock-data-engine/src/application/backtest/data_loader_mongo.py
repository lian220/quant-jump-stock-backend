"""
MongoDB Data Loader

MongoDB의 daily_stock_data 컬렉션에서 백테스트용 데이터를 로드합니다.

사용 예시:
    from application.backtest.data_loader_mongo import MongoDataLoader

    loader = MongoDataLoader(
        uri="mongodb://user:pass@localhost:27017/stock_trading?authSource=admin"
    )
    data = loader.load(
        symbols=["AAPL", "NVDA"],
        start_date=date(2025, 2, 1),
        end_date=date(2025, 2, 28)
    )
"""

import logging
import os
from datetime import date, timedelta
from typing import Dict, List, Optional

import pandas as pd
from pymongo import MongoClient

from .data_loader import DataLoader

logger = logging.getLogger(__name__)

# 레거시 데이터 포맷 (close_price만 있는 경우) OHLCV 추정 배율
_LEGACY_OPEN_RATIO = 0.998
_LEGACY_HIGH_RATIO = 1.005
_LEGACY_LOW_RATIO = 0.995
_LEGACY_VOLUME_DEFAULT = 1_000_000

class MongoDataLoader(DataLoader):
    """
    MongoDB daily_stock_data 컬렉션에서 데이터 로드

    Note:
        - 새 포맷: OHLCV 전체 (open, high, low, close, volume)
        - 레거시 포맷: close_price만 있는 경우 추정값 사용
    """

    def __init__(
        self,
        uri: Optional[str] = None,
        database: str = "stock_trading",
        collection: str = "daily_stock_data",
        add_buffer_days: int = 50
    ):
        """
        Args:
            uri: MongoDB 연결 URI (미지정 시 MONGODB_URI 환경 변수 사용)
            database: 데이터베이스 이름
            collection: 컬렉션 이름
            add_buffer_days: 지표 계산을 위한 추가 버퍼 일수 (이동평균 등)
        """
        self.uri = uri or os.environ.get("MONGODB_URI")
        if not self.uri:
            raise ValueError(
                "MongoDB URI must be provided via the `uri` parameter "
                "or the MONGODB_URI environment variable."
            )
        self.database = database
        self.collection = collection
        self.add_buffer_days = add_buffer_days
        self._client: Optional[MongoClient] = None

    def _get_client(self) -> MongoClient:
        """MongoDB 클라이언트 (lazy initialization)"""
        if self._client is None:
            self._client = MongoClient(self.uri)
        return self._client

    def load_single(
        self,
        symbol: str,
        start_date: date,
        end_date: date
    ) -> pd.DataFrame:
        """
        단일 종목 데이터 로드

        Args:
            symbol: 종목 코드
            start_date: 시작일
            end_date: 종료일

        Returns:
            DataFrame with OHLCV columns
        """
        result = self.load([symbol], start_date, end_date)
        return result.get(symbol, pd.DataFrame())

    def load(
        self,
        symbols: List[str],
        start_date: date,
        end_date: date,
        include_sentiment: bool = False,
        include_recommendations: bool = False,
        include_fundamentals: bool = False
    ) -> Dict[str, pd.DataFrame]:
        """
        MongoDB에서 주식 데이터 로드 (감성 분석 + 추천 지표 + 펀더멘탈 통합)

        Args:
            symbols: 종목 코드 리스트 (예: ["AAPL", "NVDA"])
            start_date: 시작일
            end_date: 종료일
            include_sentiment: 감성 분석 데이터 포함 여부
            include_recommendations: 기술적 추천 데이터 포함 여부
            include_fundamentals: 펀더멘탈 데이터 포함 여부 (info 필드에서 추출)

        Returns:
            {symbol: DataFrame} 딕셔너리
            DataFrame columns: open, high, low, close, volume
                              [sentiment_score, sentiment_count] (옵션)
                              [is_recommended, rec_rsi, rec_score] (옵션)
                              [per, pbr, dividend_yield, roe, earnings_growth, debt_to_equity, forward_pe] (옵션)
        """
        client = self._get_client()
        db = client[self.database]
        coll = db[self.collection]

        # 지표 계산을 위한 버퍼 추가 (이동평균 등)
        buffered_start = start_date - timedelta(days=self.add_buffer_days)

        logger.info(
            f"Loading {len(symbols)} symbols from MongoDB: "
            f"{buffered_start} to {end_date} (buffer: {self.add_buffer_days} days)"
        )

        # 날짜 범위 쿼리
        query = {
            "date": {
                "$gte": buffered_start.isoformat(),
                "$lte": end_date.isoformat()
            }
        }

        # 데이터 조회
        cursor = coll.find(query).sort("date", 1)
        documents = list(cursor)

        if not documents:
            logger.warning(f"No data found for {start_date} ~ {end_date}")
            return {}

        logger.info(f"Loaded {len(documents)} days from MongoDB")

        # 감성 분석 / 추천 데이터 로드 (옵션)
        sentiment_data = {}
        recommendation_data = {}

        if include_sentiment or include_recommendations:
            db = client[self.database]

            if include_sentiment:
                sentiment_coll = db["sentiment_analysis"]
                sentiment_docs = list(sentiment_coll.find({
                    "date": {
                        "$gte": buffered_start.isoformat(),
                        "$lte": end_date.isoformat()
                    }
                }))
                # {(ticker, date): {...}}
                for doc in sentiment_docs:
                    key = (doc.get("ticker"), doc.get("date"))
                    sentiment_data[key] = doc
                logger.info(f"Loaded {len(sentiment_docs)} sentiment records")
                if sentiment_docs:
                    logger.debug(f"Sample sentiment keys: {list(sentiment_data.keys())[:5]}")

            if include_recommendations:
                rec_coll = db["stock_recommendations"]
                rec_docs = list(rec_coll.find({
                    "date": {
                        "$gte": buffered_start.isoformat(),
                        "$lte": end_date.isoformat()
                    }
                }))
                # {(ticker, date): {...}}
                for doc in rec_docs:
                    key = (doc.get("ticker"), doc.get("date"))
                    recommendation_data[key] = doc
                logger.info(f"Loaded {len(rec_docs)} recommendation records")

        # 종목별 데이터 변환 (Left Join 적용)
        result: Dict[str, pd.DataFrame] = {}

        for symbol in symbols:
            records = []

            for doc in documents:
                date_str = doc["date"]
                stocks = doc.get("stocks", {})
                stock_data = stocks.get(symbol)

                if stock_data:
                    # OHLCV 데이터 추출 (새 포맷 우선, 없으면 close_price로 폴백)
                    close = stock_data.get("close") or stock_data.get("close_price")
                    if close is None:
                        continue

                    close = float(close)

                    # 기본 OHLCV 레코드 생성
                    record = {
                        "date": pd.Timestamp(date_str)
                    }

                    # 새 포맷 (OHLCV 전체)
                    if "open" in stock_data:
                        record.update({
                            "open": float(stock_data["open"]) if stock_data.get("open") else close,
                            "high": float(stock_data["high"]) if stock_data.get("high") else close,
                            "low": float(stock_data["low"]) if stock_data.get("low") else close,
                            "close": close,
                            "volume": int(stock_data.get("volume", 0))
                        })
                    else:
                        # 레거시 포맷 (close_price만 있는 경우) - 추정값 사용
                        record.update({
                            "open": close * _LEGACY_OPEN_RATIO,
                            "high": close * _LEGACY_HIGH_RATIO,
                            "low": close * _LEGACY_LOW_RATIO,
                            "close": close,
                            "volume": _LEGACY_VOLUME_DEFAULT
                        })

                    # Left Join: 펀더멘탈 데이터 추가 (info 필드)
                    # 누락된 값은 NaN으로 표시 (0.0과 구분하기 위해)
                    if include_fundamentals:
                        info = stock_data.get("info", {}) if isinstance(stock_data, dict) else {}
                        _nan = float('nan')
                        record["per"] = float(info["trailingPE"]) if info.get("trailingPE") is not None else _nan
                        record["pbr"] = float(info["priceToBook"]) if info.get("priceToBook") is not None else _nan
                        record["dividend_yield"] = float(info["dividendYield"]) if info.get("dividendYield") is not None else _nan
                        record["roe"] = float(info["returnOnEquity"]) if info.get("returnOnEquity") is not None else _nan
                        record["earnings_growth"] = float(info["earningsGrowth"]) if info.get("earningsGrowth") is not None else _nan
                        record["debt_to_equity"] = float(info["debtToEquity"]) if info.get("debtToEquity") is not None else _nan
                        record["forward_pe"] = float(info["forwardPE"]) if info.get("forwardPE") is not None else _nan

                    # Left Join: 감성 분석 데이터 추가
                    if include_sentiment:
                        sent_key = (symbol, date_str)
                        sent_doc = sentiment_data.get(sent_key)
                        if sent_doc:
                            score = float(sent_doc.get("average_sentiment_score", 0.0))
                            count = int(sent_doc.get("article_count", 0))
                            record["sentiment_score"] = score
                            record["sentiment_count"] = count
                            logger.debug(f"Sentiment matched {sent_key}: score={score:.4f}, count={count}")
                        else:
                            record["sentiment_score"] = 0.0
                            record["sentiment_count"] = 0
                            logger.debug(f"Sentiment not found for {sent_key}, using defaults")

                    # Left Join: 추천 데이터 추가
                    if include_recommendations:
                        rec_key = (symbol, date_str)
                        rec_doc = recommendation_data.get(rec_key)
                        if rec_doc:
                            record["is_recommended"] = bool(rec_doc.get("is_recommended", False))
                            record["rec_rsi"] = float(rec_doc.get("rsi", 0.0))
                            record["rec_score"] = float(rec_doc.get("recommendation_score", 0.0))
                        else:
                            record["is_recommended"] = False
                            record["rec_rsi"] = 0.0
                            record["rec_score"] = 0.0

                    records.append(record)

            if records:
                df = pd.DataFrame(records)
                df.set_index("date", inplace=True)
                df.sort_index(inplace=True)
                result[symbol] = df
                logger.debug(f"Loaded {len(df)} days for {symbol} (sentiment={include_sentiment}, rec={include_recommendations})")
            else:
                logger.warning(f"No data for symbol: {symbol}")

        return result

    def load_benchmark(
        self,
        benchmark_ticker: str,
        start_date: date,
        end_date: date,
        ticker_to_name_map: Optional[Dict[str, str]] = None
    ) -> Optional[pd.DataFrame]:
        """
        벤치마크 데이터 로드

        로딩 전략:
        1. doc["stocks"][ticker] 에서 로드 (SPY, QQQ 등 ETF)
        2. 없으면 doc["yfinance_indicators"][ticker] 에서 로드 (^GSPC 등 인덱스)

        Args:
            benchmark_ticker: 벤치마크 ticker (예: "SPY", "^GSPC")
            start_date: 시작일
            end_date: 종료일
            ticker_to_name_map: (deprecated, 미사용) 하위 호환용 파라미터. 향후 제거 예정.

        Returns:
            DataFrame with close column, or None if not found
        """
        if ticker_to_name_map is not None:
            logger.warning("ticker_to_name_map is deprecated and will be removed in a future version")

        client = self._get_client()
        db = client[self.database]
        coll = db[self.collection]

        query = {
            "date": {
                "$gte": start_date.isoformat(),
                "$lte": end_date.isoformat()
            }
        }

        cursor = coll.find(query).sort("date", 1)
        documents = list(cursor)

        if not documents:
            logger.warning(f"No benchmark data for {start_date} ~ {end_date}")
            return None

        records = []

        for doc in documents:
            close_value = None

            # 1차 시도: stocks에서 로드 (ETF: SPY, QQQ 등)
            stocks = doc.get("stocks", {})
            stock_data = stocks.get(benchmark_ticker)
            if stock_data:
                close_value = stock_data.get("close") or stock_data.get("close_price")

            # 2차 시도: yfinance_indicators에서 ticker로 직접 조회
            if close_value is None:
                yf_indicators = doc.get("yfinance_indicators", {})
                raw_value = yf_indicators.get(benchmark_ticker)

                # dict(OHLCV)이면 close 추출, float이면 그대로 (레거시 호환)
                if isinstance(raw_value, dict):
                    close_value = raw_value.get("close") or raw_value.get("close_price")
                else:
                    close_value = raw_value

            if close_value is not None:
                records.append({
                    "date": pd.Timestamp(doc["date"]),
                    "close": float(close_value)
                })

        if not records:
            logger.warning(f"No data found for benchmark: {benchmark_ticker}")
            return None

        df = pd.DataFrame(records)
        df.set_index("date", inplace=True)
        df.sort_index(inplace=True)

        logger.info(f"Loaded {len(df)} days of benchmark data for {benchmark_ticker}")
        return df

    def get_available_symbols(self) -> List[str]:
        """사용 가능한 종목 목록 조회"""
        client = self._get_client()
        db = client[self.database]
        coll = db[self.collection]

        # 최신 문서에서 종목 목록 추출
        latest = coll.find_one(sort=[("date", -1)])
        if latest and "stocks" in latest:
            return list(latest["stocks"].keys())
        return []

    def get_date_range(self) -> tuple:
        """데이터 날짜 범위 조회"""
        client = self._get_client()
        db = client[self.database]
        coll = db[self.collection]

        pipeline = [
            {"$group": {
                "_id": None,
                "min_date": {"$min": "$date"},
                "max_date": {"$max": "$date"},
                "count": {"$sum": 1}
            }}
        ]

        result = list(coll.aggregate(pipeline))
        if result:
            r = result[0]
            return r["min_date"], r["max_date"], r["count"]
        return None, None, 0

    def close(self):
        """연결 종료"""
        if self._client:
            self._client.close()
            self._client = None
