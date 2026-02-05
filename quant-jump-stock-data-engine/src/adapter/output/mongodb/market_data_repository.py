"""
MongoDB Market Data Repository

시세 및 기술적 지표 데이터를 MongoDB에서 조회하는 어댑터.
"""

import logging
from typing import List, Optional, Dict
from datetime import datetime
import pandas as pd
from pymongo.database import Database

from application.ports.output_ports import MarketDataPort, PriceData, IndicatorData

logger = logging.getLogger(__name__)


class MongoMarketDataRepository(MarketDataPort):
    """
    MongoDB 시장 데이터 저장소

    주가 및 기술적 지표 데이터 조회.
    """

    PRICE_COLLECTION = "stock_prices"
    INDICATOR_COLLECTION = "technical_indicators"

    def __init__(self, db: Database):
        self.db = db
        self.price_collection = db[self.PRICE_COLLECTION]
        self.indicator_collection = db[self.INDICATOR_COLLECTION]

    async def get_latest_price(self, symbol: str) -> Optional[PriceData]:
        """최신 시세 조회"""
        doc = self.price_collection.find_one(
            {"symbol": symbol},
            sort=[("timestamp", -1)]
        )

        if doc is None:
            return None

        return PriceData(
            symbol=doc["symbol"],
            timestamp=doc["timestamp"],
            open=doc["open"],
            high=doc["high"],
            low=doc["low"],
            close=doc["close"],
            volume=doc.get("volume", 0)
        )

    async def get_price_history(
        self,
        symbol: str,
        start_date: datetime,
        end_date: Optional[datetime] = None,
        limit: int = 200
    ) -> pd.DataFrame:
        """
        과거 시세 조회

        Returns:
            DataFrame with columns: [open, high, low, close, volume]
            Index: DatetimeIndex
        """
        query = {
            "symbol": symbol,
            "timestamp": {"$gte": start_date}
        }

        if end_date:
            query["timestamp"]["$lte"] = end_date

        cursor = self.price_collection.find(
            query,
            sort=[("timestamp", 1)],
            limit=limit
        )

        docs = list(cursor)

        if not docs:
            return pd.DataFrame()

        df = pd.DataFrame(docs)

        # 인덱스 설정
        if "timestamp" in df.columns:
            df["timestamp"] = pd.to_datetime(df["timestamp"])
            df.set_index("timestamp", inplace=True)

        # 필요한 컬럼만 선택
        columns = ["open", "high", "low", "close", "volume"]
        available_columns = [c for c in columns if c in df.columns]

        return df[available_columns]

    async def get_indicators(
        self,
        symbol: str,
        indicator_names: List[str]
    ) -> Optional[IndicatorData]:
        """
        기술적 지표 조회

        Args:
            symbol: 종목 코드
            indicator_names: 필요한 지표 목록 (e.g., ["sma_20", "rsi_14"])
        """
        doc = self.indicator_collection.find_one(
            {"symbol": symbol},
            sort=[("timestamp", -1)]
        )

        if doc is None:
            return None

        indicators = {}
        for name in indicator_names:
            if name in doc:
                indicators[name] = doc[name]

        return IndicatorData(
            symbol=doc["symbol"],
            timestamp=doc["timestamp"],
            indicators=indicators
        )

    async def get_multiple_symbols_latest(
        self,
        symbols: List[str]
    ) -> Dict[str, PriceData]:
        """여러 종목의 최신 시세 일괄 조회"""
        result = {}

        # Aggregation pipeline으로 각 심볼의 최신 데이터 조회
        pipeline = [
            {"$match": {"symbol": {"$in": symbols}}},
            {"$sort": {"timestamp": -1}},
            {"$group": {
                "_id": "$symbol",
                "doc": {"$first": "$$ROOT"}
            }}
        ]

        cursor = self.price_collection.aggregate(pipeline)

        for item in cursor:
            doc = item["doc"]
            symbol = doc["symbol"]
            result[symbol] = PriceData(
                symbol=symbol,
                timestamp=doc["timestamp"],
                open=doc["open"],
                high=doc["high"],
                low=doc["low"],
                close=doc["close"],
                volume=doc.get("volume", 0)
            )

        return result
