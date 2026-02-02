from pymongo import MongoClient
import psycopg2
import psycopg2.extras
from contextlib import contextmanager
from src.core.config import settings
import logging

logger = logging.getLogger(__name__)

class MongoDB:
    _client = None

    @classmethod
    def get_client(cls):
        if cls._client is None:
            try:
                cls._client = MongoClient(settings.MONGODB_URI)
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


class PostgreSQL:
    """PostgreSQL 연결 관리 클래스"""

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
    @contextmanager
    def get_connection(cls):
        """PostgreSQL 연결 컨텍스트 매니저"""
        conn = None
        try:
            conn = psycopg2.connect(**cls.get_connection_params())
            yield conn
            conn.commit()
        except Exception as e:
            if conn:
                conn.rollback()
            logger.error(f"PostgreSQL error: {e}")
            raise
        finally:
            if conn:
                conn.close()

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
