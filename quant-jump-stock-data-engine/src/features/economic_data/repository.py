"""Economic Data Repository - MongoDB 및 PostgreSQL 데이터 접근"""
import logging
from typing import Dict, Any, List, Optional
from datetime import datetime
import psycopg2
from psycopg2.extras import RealDictCursor
from core.database import MongoDB
from core.config import settings

logger = logging.getLogger(__name__)


class EconomicDataRepository:
    """경제 데이터 저장소"""

    def __init__(self):
        self.db = MongoDB.get_db()

    def save_data(self, collection: str, data: Dict[str, Any]) -> bool:
        """데이터를 저장합니다."""
        try:
            if self.db is None:
                logger.error("MongoDB 연결 없음")
                return False

            collection_obj = self.db[collection]

            # upsert 방식으로 저장 (중복 방지)
            filter_query = {"date": data.get("date")}

            if "code" in data:
                filter_query["code"] = data["code"]
            elif "ticker" in data:
                filter_query["ticker"] = data["ticker"]

            collection_obj.update_one(
                filter_query,
                {"$set": data},
                upsert=True
            )

            return True

        except Exception as e:
            logger.error(f"데이터 저장 실패: {e}")
            return False

    def find_active_indicators(self, collection: str) -> List[Dict[str, Any]]:
        """활성화된 지표를 조회합니다."""
        try:
            if self.db is None:
                logger.error("MongoDB 연결 없음")
                return []

            collection_obj = self.db[collection]
            return list(collection_obj.find({"is_active": True}))

        except Exception as e:
            logger.error(f"지표 조회 실패: {e}")
            return []

    def find_active_stocks(self) -> List[Dict[str, Any]]:
        """활성화된 종목을 조회합니다."""
        try:
            if self.db is None:
                logger.error("MongoDB 연결 없음")
                return []

            collection = self.db["stocks"]
            return list(collection.find({"is_active": True}))

        except Exception as e:
            logger.error(f"종목 조회 실패: {e}")
            return []

    def upsert_daily_data(self, date: str, data: Dict[str, Any]) -> bool:
        """
        daily_stock_data 컬렉션에 날짜별 데이터를 upsert합니다.

        Args:
            date: 날짜 (YYYY-MM-DD 형식)
            data: {
                "fred_indicators": {"GDP": 123.45, ...},
                "yfinance_indicators": {"SP500": 4500.12, ...},
                "stocks": {"AAPL": {"close_price": 150.0}, ...}
            }

        Returns:
            성공 여부
        """
        try:
            if self.db is None:
                logger.error("MongoDB 연결 없음")
                return False

            collection = self.db["daily_stock_data"]

            # per-key 병합 upsert (clobber 방지)
            #
            # 배경 (2026-06-15 사고): fred_indicators/yfinance_indicators/stocks 를
            # 통째로 $set 하면, 같은 거래일을 재수집하는 후속 런이 부분 실패(예: yfinance
            # throttle 로 42→18 종목)했을 때 이미 완전했던 stocks 맵을 통째 덮어써 데이터가
            # 영구 손실된다. 하위 키(티커/지표코드)만 dot-path 로 개별 set 하여, 이번 런이
            # 가져온 것만 갱신하고 나머지 기존 값은 보존한다.
            MERGE_KEYS = ("fred_indicators", "yfinance_indicators", "stocks")
            set_fields: Dict[str, Any] = {"updated_at": datetime.now()}
            for top_key, value in data.items():
                if top_key in MERGE_KEYS and isinstance(value, dict):
                    # dot/$ 포함 키는 dot-path 가 의도치 않은 중첩을 만들므로 그 맵만 통째 set
                    if any("." in str(k) or str(k).startswith("$") for k in value):
                        set_fields[top_key] = value
                    else:
                        for sub_key, sub_val in value.items():
                            set_fields[f"{top_key}.{sub_key}"] = sub_val
                else:
                    set_fields[top_key] = value

            result = collection.update_one(
                {"date": date},
                {"$set": set_fields},
                upsert=True
            )

            return result.acknowledged

        except Exception as e:
            logger.error(f"Daily data upsert 실패 (date={date}): {e}")
            return False

    def find_latest_date(self) -> Optional[str]:
        """
        daily_stock_data에서 가장 최신 날짜를 조회합니다.

        Returns:
            가장 최근 날짜 문자열 (YYYY-MM-DD) 또는 None
        """
        try:
            if self.db is None:
                logger.error("MongoDB 연결 없음")
                return None

            collection = self.db["daily_stock_data"]
            doc = collection.find_one(
                sort=[("date", -1)],
                projection={"date": 1, "_id": 0}
            )

            if doc and "date" in doc:
                logger.debug(f"MongoDB 최신 수집일: {doc['date']}")
                return doc["date"]

            return None

        except Exception as e:
            logger.error(f"최신 날짜 조회 실패: {e}")
            return None

    def _get_postgres_connection(self):
        """PostgreSQL 연결을 생성합니다."""
        try:
            return psycopg2.connect(
                host=settings.POSTGRES_HOST,
                port=settings.POSTGRES_PORT,
                database=settings.POSTGRES_DB,
                user=settings.POSTGRES_USER,
                password=settings.POSTGRES_PASSWORD
            )
        except Exception as e:
            logger.error(f"PostgreSQL 연결 실패: {e}")
            raise

    def find_active_fred_indicators(self) -> List[Dict[str, Any]]:
        """PostgreSQL에서 활성화된 FRED 지표를 조회합니다."""
        try:
            conn = self._get_postgres_connection()
            cursor = conn.cursor(cursor_factory=RealDictCursor)

            cursor.execute("""
                SELECT code, name, description, category, unit, frequency
                FROM fred_indicators
                WHERE is_active = true
                ORDER BY code
            """)

            results = cursor.fetchall()
            cursor.close()
            conn.close()

            return [dict(row) for row in results]

        except Exception as e:
            logger.error(f"FRED 지표 조회 실패 (PostgreSQL): {e}")
            return []

    def find_active_yfinance_indicators(self) -> List[Dict[str, Any]]:
        """PostgreSQL에서 활성화된 Yahoo Finance 지표를 조회합니다."""
        try:
            conn = self._get_postgres_connection()
            cursor = conn.cursor(cursor_factory=RealDictCursor)

            cursor.execute("""
                SELECT ticker, name, description, indicator_type
                FROM yfinance_indicators
                WHERE is_active = true
                ORDER BY ticker
            """)

            results = cursor.fetchall()
            cursor.close()
            conn.close()

            return [dict(row) for row in results]

        except Exception as e:
            logger.error(f"Yahoo Finance 지표 조회 실패 (PostgreSQL): {e}")
            return []

    def find_active_stocks_from_postgres(self) -> List[Dict[str, Any]]:
        """PostgreSQL에서 활성화된 종목을 조회합니다."""
        try:
            conn = self._get_postgres_connection()
            cursor = conn.cursor(cursor_factory=RealDictCursor)

            cursor.execute("""
                SELECT ticker, stock_name, stock_name_en, is_etf, exchange, sector, industry
                FROM stocks
                WHERE is_active = true
                ORDER BY ticker
            """)

            results = cursor.fetchall()
            cursor.close()
            conn.close()

            return [dict(row) for row in results]

        except Exception as e:
            logger.error(f"종목 조회 실패 (PostgreSQL): {e}")
            return []
