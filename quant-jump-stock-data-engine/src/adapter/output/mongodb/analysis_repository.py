"""
MongoDB Analysis Repository Adapters

분석 서비스용 MongoDB 어댑터.
"""

import logging
from typing import List, Dict, Optional
from datetime import datetime
from pymongo.database import Database

from application.analysis.ports import (
    PriceRepositoryPort,
    AnalysisResultRepositoryPort,
    DailyPrice,
    TechnicalResult,
)

logger = logging.getLogger(__name__)


class MongoPriceRepository(PriceRepositoryPort):
    """MongoDB 가격 데이터 저장소"""

    def __init__(self, db: Database):
        self.db = db
        self.collection = db["daily_stock_data"]

    async def get_daily_prices(
        self,
        start_date: str,
        end_date: str
    ) -> Dict[str, List[DailyPrice]]:
        """일별 가격 데이터 조회 (projection 최적화: stocks 필드만 로드)"""
        cursor = self.collection.find(
            {"date": {"$gte": start_date, "$lte": end_date}},
            {"date": 1, "stocks": 1, "_id": 0}
        ).sort("date", 1)

        result: Dict[str, List[DailyPrice]] = {}

        for doc in cursor:
            date = doc["date"]
            stocks_data = doc.get("stocks", {})

            for ticker, val in stocks_data.items():
                if isinstance(val, (int, float)):
                    price = float(val)
                elif isinstance(val, dict):
                    price = val.get("close_price") or val.get("close")
                    if price is None:
                        continue
                    price = float(price)
                else:
                    continue

                if ticker not in result:
                    result[ticker] = []

                result[ticker].append(DailyPrice(
                    ticker=ticker,
                    date=date,
                    close_price=price
                ))

        return result


class MongoAnalysisResultRepository(AnalysisResultRepositoryPort):
    """MongoDB 분석 결과 저장소"""

    def __init__(self, db: Database):
        self.db = db
        self.collection = db["stock_recommendations"]

    async def save_technical_result(self, result: TechnicalResult) -> None:
        """단일 결과 저장"""
        doc = self._to_document(result)
        self.collection.update_one(
            {"ticker": result.ticker, "date": result.date},
            {"$set": doc},
            upsert=True
        )

    async def save_batch(self, results: List[TechnicalResult]) -> None:
        """일괄 저장"""
        from pymongo import UpdateOne

        if not results:
            return

        operations = [
            UpdateOne(
                {"ticker": r.ticker, "date": r.date},
                {"$set": self._to_document(r)},
                upsert=True
            )
            for r in results
        ]

        self.collection.bulk_write(operations)
        logger.info(f"Saved {len(results)} technical analysis results")

    def _to_document(self, result: TechnicalResult) -> dict:
        """TechnicalResult → MongoDB 문서.

        2026-05-20 hotfix: `date` 는 ISODate 로 통일 저장. 만약 legacy string 이
        흘러들어와도 BSON 직전에 datetime 으로 안전 변환 (defensive).
        """
        date_value = result.date
        if isinstance(date_value, str):
            date_value = datetime.strptime(date_value, "%Y-%m-%d")
        return {
            "ticker": result.ticker,
            "stock_name": result.stock_name,
            "date": date_value,
            "technical_indicators": {
                "sma20": result.sma20,
                "sma50": result.sma50,
                "rsi": result.rsi,
                "macd": result.macd,
                "signal": result.signal,
                "golden_cross": result.golden_cross,
                "macd_buy_signal": result.macd_buy_signal,
            },
            "is_recommended": result.is_recommended,
            "updated_at": datetime.utcnow()
        }
