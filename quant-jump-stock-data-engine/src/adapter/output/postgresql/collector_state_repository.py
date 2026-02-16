"""
PostgreSQL Collector State Repository

뉴스 수집기 상태를 PostgreSQL collector_state 테이블에서 관리.
"""

import logging
from datetime import datetime
from typing import Optional
from contextlib import contextmanager

import psycopg2.extras

from domain.news.models import CollectorState, NewsSource

logger = logging.getLogger(__name__)


class PostgresCollectorStateRepository:
    """PostgreSQL 수집기 상태 저장소"""

    def __init__(self, pool):
        self._pool = pool

    @contextmanager
    def _get_connection(self):
        conn = None
        try:
            conn = self._pool.getconn()
            yield conn
        except Exception as e:
            if conn:
                try:
                    conn.rollback()
                except Exception:
                    pass
            logger.error(f"PostgreSQL connection error: {e}")
            raise
        finally:
            if conn:
                try:
                    conn.commit()  # 읽기 쿼리도 implicit transaction 종료 필요
                except Exception:
                    pass
                self._pool.putconn(conn)

    def get_state(self, source: NewsSource) -> Optional[CollectorState]:
        """수집기 상태 조회"""
        with self._get_connection() as conn:
            with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
                cursor.execute(
                    "SELECT * FROM news_collector_state WHERE source = %s",
                    (source.value,),
                )
                row = cursor.fetchone()
        if not row:
            return None
        return CollectorState(
            source=NewsSource(row["source"]),
            last_fetched_at=row.get("last_fetched_at"),
            last_fetched_id=row.get("last_fetched_id"),
            fetch_count=row.get("fetch_count", 0),
            consecutive_errors=row.get("consecutive_errors", 0),
            total_errors=row.get("total_errors", 0),
            last_response_time_ms=row.get("avg_response_time_ms", 0),
            last_error=row.get("last_error"),
            last_error_at=row.get("last_error_at"),
            updated_at=row.get("updated_at"),
        )

    def get_last_fetched_at(self, source: NewsSource) -> Optional[datetime]:
        with self._get_connection() as conn:
            with conn.cursor() as cursor:
                cursor.execute(
                    "SELECT last_fetched_at FROM news_collector_state WHERE source = %s",
                    (source.value,),
                )
                row = cursor.fetchone()
        return row[0] if row else None

    def get_last_fetched_id(self, source: NewsSource) -> Optional[str]:
        with self._get_connection() as conn:
            with conn.cursor() as cursor:
                cursor.execute(
                    "SELECT last_fetched_id FROM news_collector_state WHERE source = %s",
                    (source.value,),
                )
                row = cursor.fetchone()
        return row[0] if row else None

    def update_state(
        self,
        source: NewsSource,
        last_fetched_at: datetime,
        last_fetched_id: Optional[str],
    ) -> None:
        """수집 상태 업데이트 (upsert)"""
        with self._get_connection() as conn:
            with conn.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO news_collector_state (source, last_fetched_at, last_fetched_id, fetch_count, updated_at)
                    VALUES (%s, %s, %s, 1, NOW())
                    ON CONFLICT (source) DO UPDATE SET
                        last_fetched_at = EXCLUDED.last_fetched_at,
                        last_fetched_id = EXCLUDED.last_fetched_id,
                        fetch_count = news_collector_state.fetch_count + 1,
                        updated_at = NOW()
                    """,
                    (source.value, last_fetched_at, last_fetched_id),
                )
            conn.commit()

    def record_success(self, source: NewsSource, response_time_ms: int) -> None:
        """성공 기록"""
        with self._get_connection() as conn:
            with conn.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO news_collector_state (source, consecutive_errors, avg_response_time_ms, updated_at)
                    VALUES (%s, 0, %s, NOW())
                    ON CONFLICT (source) DO UPDATE SET
                        consecutive_errors = 0,
                        avg_response_time_ms = EXCLUDED.avg_response_time_ms,
                        updated_at = NOW()
                    """,
                    (source.value, response_time_ms),
                )
            conn.commit()

    def record_error(self, source: NewsSource, error: str) -> None:
        """에러 기록"""
        with self._get_connection() as conn:
            with conn.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO news_collector_state (source, consecutive_errors, total_errors, last_error, last_error_at, updated_at)
                    VALUES (%s, 1, 1, %s, NOW(), NOW())
                    ON CONFLICT (source) DO UPDATE SET
                        consecutive_errors = news_collector_state.consecutive_errors + 1,
                        total_errors = news_collector_state.total_errors + 1,
                        last_error = EXCLUDED.last_error,
                        last_error_at = NOW(),
                        updated_at = NOW()
                    """,
                    (source.value, error),
                )
            conn.commit()
