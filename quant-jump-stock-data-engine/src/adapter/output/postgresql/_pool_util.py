"""PostgreSQL ThreadedConnectionPool 공통 헬퍼 (2026-05-29 사고 후속).

배경:
  2026-05-27 사고 (7일간 추천 마비) 직접 원인 = pool 에 idle connection 이 Supabase 측
  timeout 으로 끊겼지만 클라이언트 측 ThreadedConnectionPool 은 인지 못함. 첫 query 시:
      psycopg2.DatabaseError: server closed the connection unexpectedly

해결:
  getconn() 후 SELECT 1 으로 alive 검증. 끊겼으면 putconn(close=True) 로 dead conn
  영구 폐기 + 새 conn 시도. 정상 conn 이면 추가 비용 < 1ms.

사용:
  from contextlib import contextmanager
  from adapter.output.postgresql._pool_util import get_validated_conn

  @contextmanager
  def _get_connection(self):
      conn = get_validated_conn(self._pool)
      try:
          yield conn
      finally:
          self._pool.putconn(conn)
"""
from __future__ import annotations

import logging
from typing import Any

logger = logging.getLogger(__name__)


def get_validated_conn(pool: Any, max_retries: int = 2):
    """pool 에서 connection 가져온 후 SELECT 1 ping. 끊겼으면 폐기 + 재시도.

    Args:
        pool: psycopg2.pool.ThreadedConnectionPool 인스턴스
        max_retries: 최대 시도 횟수 (default 2 = 첫 시도 실패 시 1회 재시도)

    Returns:
        alive 한 connection. caller 가 finally 에서 putconn 책임.

    Raises:
        마지막 시도의 예외. 또는 pool 고갈 시 RuntimeError.
    """
    last_exc = None
    for attempt in range(max_retries):
        conn = pool.getconn()
        try:
            with conn.cursor() as cur:
                cur.execute("SELECT 1")
                cur.fetchone()
            return conn  # ✅ alive
        except Exception as e:
            last_exc = e
            logger.warning(
                "PostgreSQL pool conn dead (attempt %d/%d): %s — close + retry",
                attempt + 1, max_retries, e,
            )
            try:
                pool.putconn(conn, close=True)  # dead conn 영구 폐기
            except Exception:
                pass
    raise last_exc if last_exc else RuntimeError("PostgreSQL pool exhausted")
