from pymongo import MongoClient, ASCENDING, DESCENDING
import psycopg2
import psycopg2.extras
import psycopg2.pool
from contextlib import contextmanager
from core.config import settings
import logging

logger = logging.getLogger(__name__)

class MongoDB:
    _client = None

    @classmethod
    def get_client(cls):
        if cls._client is None:
            try:
                cls._client = MongoClient(settings.MONGODB_URI, maxPoolSize=3)
                # Test connection
                cls._client.admin.command('ping')
                logger.info("MongoDB connection successful")
            except Exception as e:
                logger.error(f"MongoDB connection failed: {e}")
                raise
        return cls._client

    @classmethod
    def get_db(cls):
        client = cls.get_client()
        return client[settings.MONGODB_DB_NAME]

    @classmethod
    def ensure_indexes(cls, db) -> None:
        """
        서비스 기동 시 필요한 MongoDB 인덱스를 생성합니다.
        이미 존재하는 인덱스는 무시(idempotent)합니다.
        """
        try:
            # daily_stock_data: date 범위 조회
            db["daily_stock_data"].create_index(
                [("date", ASCENDING)],
                name="idx_date",
                background=True
            )

            # stock_recommendations: ticker + date upsert 및 조회
            db["stock_recommendations"].create_index(
                [("ticker", ASCENDING), ("date", ASCENDING)],
                name="idx_ticker_date",
                unique=True,
                background=True
            )

            logger.info("MongoDB indexes ensured")
        except Exception as e:
            logger.warning(f"MongoDB index creation warning: {e}")


class PostgreSQL:
    """PostgreSQL 연결 관리 클래스 (ThreadedConnectionPool 기반)"""

    _connection_pool = None

    @classmethod
    def get_connection_params(cls):
        """연결 파라미터 반환"""
        return {
            "host": settings.POSTGRES_HOST,
            "port": settings.POSTGRES_PORT,
            "database": settings.POSTGRES_DB,
            "user": settings.POSTGRES_USER,
            "password": settings.POSTGRES_PASSWORD
        }

    @classmethod
    def get_pool(cls):
        """ThreadedConnectionPool 싱글톤 반환 (minconn=1, maxconn=4)"""
        if cls._connection_pool is None:
            try:
                cls._connection_pool = psycopg2.pool.ThreadedConnectionPool(
                    minconn=1,
                    maxconn=4,
                    **cls.get_connection_params()
                )
                logger.info("PostgreSQL ThreadedConnectionPool created (min=1, max=3)")
            except Exception as e:
                logger.error(f"Failed to create PostgreSQL connection pool: {e}")
                raise
        return cls._connection_pool

    @classmethod
    @contextmanager
    def get_connection(cls):
        """PostgreSQL 연결 컨텍스트 매니저 (풀에서 가져오고 반환)"""
        pool = cls.get_pool()
        conn = None
        try:
            conn = pool.getconn()
            yield conn
            conn.commit()
        except Exception as e:
            if conn:
                conn.rollback()
            logger.error(f"PostgreSQL error: {e}")
            raise
        finally:
            if conn:
                pool.putconn(conn)

    @classmethod
    def close_pool(cls):
        """커넥션 풀 종료 (앱 종료 시 호출)"""
        if cls._connection_pool is not None:
            cls._connection_pool.closeall()
            cls._connection_pool = None
            logger.info("PostgreSQL connection pool closed")

    @classmethod
    def execute_query(cls, query, params=None, fetch_one=False, fetch_all=True):
        """쿼리 실행 헬퍼 메서드"""
        with cls.get_connection() as conn:
            with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
                cursor.execute(query, params)
                if fetch_one:
                    return cursor.fetchone()
                elif fetch_all:
                    return cursor.fetchall()
                else:
                    return cursor.rowcount
