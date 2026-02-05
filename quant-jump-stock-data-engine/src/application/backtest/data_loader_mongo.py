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
from datetime import date
from typing import Dict, List, Optional

import pandas as pd
from pymongo import MongoClient

from .data_loader import DataLoader

logger = logging.getLogger(__name__)


class MongoDataLoader(DataLoader):
    """
    MongoDB daily_stock_data 컬렉션에서 데이터 로드

    Note:
        - 새 포맷: OHLCV 전체 (open, high, low, close, volume)
        - 레거시 포맷: close_price만 있는 경우 추정값 사용
    """

    def __init__(
        self,
        uri: str = "mongodb://quantiq_user:quantiq_password@localhost:27017/stock_trading?authSource=admin",
        database: str = "stock_trading",
        collection: str = "daily_stock_data"
    ):
        """
        Args:
            uri: MongoDB 연결 URI
            database: 데이터베이스 이름
            collection: 컬렉션 이름
        """
        self.uri = uri
        self.database = database
        self.collection = collection
        self._client: Optional[MongoClient] = None

    def _get_client(self) -> MongoClient:
        """MongoDB 클라이언트 (lazy initialization)"""
        if self._client is None:
            self._client = MongoClient(self.uri)
        return self._client

    def load(
        self,
        symbols: List[str],
        start_date: date,
        end_date: date
    ) -> Dict[str, pd.DataFrame]:
        """
        MongoDB에서 주식 데이터 로드

        Args:
            symbols: 종목 코드 리스트 (예: ["AAPL", "NVDA"])
            start_date: 시작일
            end_date: 종료일

        Returns:
            {symbol: DataFrame} 딕셔너리
            DataFrame columns: open, high, low, close, volume
        """
        client = self._get_client()
        db = client[self.database]
        coll = db[self.collection]

        # 날짜 범위 쿼리
        query = {
            "date": {
                "$gte": start_date.isoformat(),
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

        # 종목별 데이터 변환
        result: Dict[str, pd.DataFrame] = {}

        for symbol in symbols:
            records = []

            for doc in documents:
                stocks = doc.get("stocks", {})
                stock_data = stocks.get(symbol)

                if stock_data:
                    # OHLCV 데이터 추출 (새 포맷 우선, 없으면 close_price로 폴백)
                    close = stock_data.get("close") or stock_data.get("close_price")
                    if close is None:
                        continue

                    close = float(close)

                    # 새 포맷 (OHLCV 전체)
                    if "open" in stock_data:
                        records.append({
                            "date": pd.Timestamp(doc["date"]),
                            "open": float(stock_data["open"]) if stock_data.get("open") else close,
                            "high": float(stock_data["high"]) if stock_data.get("high") else close,
                            "low": float(stock_data["low"]) if stock_data.get("low") else close,
                            "close": close,
                            "volume": int(stock_data.get("volume", 0))
                        })
                    else:
                        # 레거시 포맷 (close_price만 있는 경우) - 추정값 사용
                        records.append({
                            "date": pd.Timestamp(doc["date"]),
                            "open": close * 0.998,
                            "high": close * 1.005,
                            "low": close * 0.995,
                            "close": close,
                            "volume": 1000000
                        })

            if records:
                df = pd.DataFrame(records)
                df.set_index("date", inplace=True)
                df.sort_index(inplace=True)
                result[symbol] = df
                logger.debug(f"Loaded {len(df)} days for {symbol}")
            else:
                logger.warning(f"No data for symbol: {symbol}")

        return result

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
