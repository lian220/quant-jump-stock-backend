"""
PostgreSQL Strategy Repository Adapter

PostgreSQL에 저장된 시드 전략을 조회하는 어댑터.
DSL 형식의 conditions JSONB를 StrategyDefinition으로 변환.

Note: 시드 전략은 읽기 전용. save/update는 지원하지 않음.
AI 생성 전략 등 동적 전략은 MongoDB에서 관리.
"""

import logging
from typing import List, Optional
import psycopg2
import psycopg2.extras
from contextlib import contextmanager
import json

from application.ports.output_ports import StrategyRepositoryPort
from domain.strategy.models import StrategyDefinition, Rule, Condition, RiskManagement

logger = logging.getLogger(__name__)


class PostgresStrategyRepository(StrategyRepositoryPort):
    """
    PostgreSQL 전략 저장소 (읽기 전용)

    PostgreSQL strategies 테이블에서 DSL 형식 전략을 조회.
    conditions JSONB는 Data Engine DSL 스키마와 동일.
    """

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

    async def find_by_id(self, strategy_id: str) -> Optional[StrategyDefinition]:
        """전략 ID로 조회"""
        try:
            with self._get_connection() as conn:
                with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
                    cursor.execute(
                        """
                        SELECT
                            s.id,
                            s.strategy_id,
                            s.name,
                            s.description,
                            s.version,
                            s.conditions,
                            s.status,
                            sc.code as category_code,
                            s.created_at,
                            s.updated_at
                        FROM strategies s
                        LEFT JOIN strategy_categories sc ON s.category_id = sc.id
                        WHERE s.strategy_id = %s AND s.status = 'ACTIVE'
                        """,
                        (strategy_id,)
                    )
                    row = cursor.fetchone()

            if row is None:
                logger.debug(f"Strategy not found: {strategy_id}")
                return None

            return self._to_domain(row)

        except Exception as e:
            logger.error(f"Failed to fetch strategy {strategy_id}: {e}")
            return None

    async def find_all_active(self) -> List[StrategyDefinition]:
        """활성화된 모든 전략 조회"""
        try:
            with self._get_connection() as conn:
                with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
                    cursor.execute(
                        """
                        SELECT
                            s.id,
                            s.strategy_id,
                            s.name,
                            s.description,
                            s.version,
                            s.conditions,
                            s.status,
                            sc.code as category_code,
                            s.created_at,
                            s.updated_at
                        FROM strategies s
                        LEFT JOIN strategy_categories sc ON s.category_id = sc.id
                        WHERE s.status = 'ACTIVE'
                            AND s.strategy_id IS NOT NULL
                        ORDER BY s.created_at DESC
                        """
                    )
                    rows = cursor.fetchall()

            strategies = []
            for row in rows:
                try:
                    strategy = self._to_domain(row)
                    if strategy:
                        strategies.append(strategy)
                except Exception as e:
                    logger.warning(f"Failed to parse strategy {row.get('strategy_id')}: {e}")
                    continue

            logger.info(f"Loaded {len(strategies)} active strategies from PostgreSQL")
            return strategies

        except Exception as e:
            logger.error(f"Failed to fetch active strategies: {e}")
            return []

    async def find_by_tags(self, tags: List[str]) -> List[StrategyDefinition]:
        """
        태그로 전략 검색

        Note: tags는 conditions JSONB 내 tags 배열에서 검색
        """
        if not tags:
            return []

        try:
            with self._get_connection() as conn:
                with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
                    # JSONB 배열에서 태그 검색 (ANY 연산자 사용)
                    cursor.execute(
                        """
                        SELECT
                            s.id,
                            s.strategy_id,
                            s.name,
                            s.description,
                            s.version,
                            s.conditions,
                            s.status,
                            sc.code as category_code,
                            s.created_at,
                            s.updated_at
                        FROM strategies s
                        LEFT JOIN strategy_categories sc ON s.category_id = sc.id
                        WHERE s.status = 'ACTIVE'
                            AND s.strategy_id IS NOT NULL
                            AND s.conditions->'tags' ?| %s
                        ORDER BY s.created_at DESC
                        """,
                        (tags,)
                    )
                    rows = cursor.fetchall()

            strategies = []
            for row in rows:
                try:
                    strategy = self._to_domain(row)
                    if strategy:
                        strategies.append(strategy)
                except Exception as e:
                    logger.warning(f"Failed to parse strategy {row.get('strategy_id')}: {e}")
                    continue

            return strategies

        except Exception as e:
            logger.error(f"Failed to search strategies by tags {tags}: {e}")
            return []

    async def save(self, strategy: StrategyDefinition) -> None:
        """
        전략 저장 - PostgreSQL에서는 지원하지 않음

        Raises:
            NotImplementedError: 시드 전략은 읽기 전용
        """
        raise NotImplementedError(
            "PostgreSQL 시드 전략은 읽기 전용입니다. "
            "동적 전략 저장은 MongoStrategyRepository를 사용하세요."
        )

    async def update(self, strategy: StrategyDefinition) -> None:
        """
        전략 업데이트 - PostgreSQL에서는 지원하지 않음

        Raises:
            NotImplementedError: 시드 전략은 읽기 전용
        """
        raise NotImplementedError(
            "PostgreSQL 시드 전략은 읽기 전용입니다. "
            "동적 전략 업데이트는 MongoStrategyRepository를 사용하세요."
        )

    def _to_domain(self, row: dict) -> Optional[StrategyDefinition]:
        """
        DB 행 → StrategyDefinition 도메인 모델 변환

        conditions JSONB 스키마:
        {
            "rules": [...],
            "risk_management": {...},
            "tags": [...]
        }
        """
        if not row:
            return None

        strategy_id = row.get("strategy_id")
        if not strategy_id:
            return None

        conditions = row.get("conditions", {})
        if isinstance(conditions, str):
            conditions = json.loads(conditions)

        # rules 파싱
        rules_data = conditions.get("rules", [])
        if not rules_data:
            logger.warning(f"Strategy {strategy_id} has no rules")
            return None

        # risk_management 파싱
        risk_data = conditions.get("risk_management", {})

        # tags 파싱
        tags = conditions.get("tags", [])

        try:
            return StrategyDefinition(
                strategy_id=strategy_id,
                name=row.get("name", strategy_id),
                description=row.get("description", ""),
                version=row.get("version", "1.0"),
                rules=rules_data,  # Pydantic이 자동 파싱
                risk_management=risk_data if risk_data else RiskManagement(),
                tags=tags,
                created_at=row.get("created_at"),
                updated_at=row.get("updated_at")
            )
        except Exception as e:
            logger.error(f"Failed to create StrategyDefinition for {strategy_id}: {e}")
            return None
