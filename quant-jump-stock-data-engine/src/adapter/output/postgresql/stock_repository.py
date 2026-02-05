"""
PostgreSQL Stock Repository Adapter

종목 정보 조회를 위한 PostgreSQL 어댑터.
"""

import logging
from typing import List
import psycopg2
import psycopg2.extras
from contextlib import contextmanager

from application.analysis.ports import StockRepositoryPort, StockInfo

logger = logging.getLogger(__name__)


class PostgresStockRepository(StockRepositoryPort):
    """PostgreSQL 종목 저장소"""

    def __init__(
        self,
        host: str,
        port: int,
        database: str,
        user: str,
        password: str
    ):
        self._conn_params = {
            "host": host,
            "port": port,
            "database": database,
            "user": user,
            "password": password
        }

    @contextmanager
    def _get_connection(self):
        """PostgreSQL 연결 컨텍스트 매니저"""
        conn = None
        try:
            conn = psycopg2.connect(**self._conn_params)
            yield conn
        except Exception as e:
            logger.error(f"PostgreSQL connection error: {e}")
            raise
        finally:
            if conn:
                conn.close()

    async def get_active_stocks(self) -> List[StockInfo]:
        """활성 종목 목록 조회"""
        try:
            with self._get_connection() as conn:
                with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
                    cursor.execute(
                        "SELECT ticker, stock_name, is_active FROM stocks WHERE is_active = TRUE"
                    )
                    rows = cursor.fetchall()

            return [
                StockInfo(
                    ticker=row["ticker"],
                    stock_name=row.get("stock_name", row["ticker"]),
                    is_active=row.get("is_active", True)
                )
                for row in rows
                if row.get("ticker")
            ]

        except Exception as e:
            logger.error(f"Failed to fetch active stocks: {e}")
            return []
