"""
시드 전략 로더

PostgreSQL에서 DSL 형식 전략을 로드합니다.
Single Source of Truth: DB만 사용 (JSON fallback 없음)

사용법:
    strategies = await load_strategies_from_db()
    strategy = await load_strategy_from_db("golden_cross")
"""
import logging
from typing import List, Optional
from domain.strategy.models import StrategyDefinition

logger = logging.getLogger(__name__)


def _get_db_config() -> dict:
    """DB 연결 설정 로드"""
    try:
        from config.settings import get_settings
        settings = get_settings()
        return {
            "host": settings.postgres_host,
            "port": settings.postgres_port,
            "database": settings.postgres_db,
            "user": settings.postgres_user,
            "password": settings.postgres_password
        }
    except ImportError:
        # fallback to core.config (레거시 호환)
        from core.config import settings as legacy_settings
        return {
            "host": legacy_settings.POSTGRES_HOST,
            "port": legacy_settings.POSTGRES_PORT,
            "database": legacy_settings.POSTGRES_DB,
            "user": legacy_settings.POSTGRES_USER,
            "password": legacy_settings.POSTGRES_PASSWORD
        }


async def load_strategies_from_db() -> List[StrategyDefinition]:
    """
    PostgreSQL에서 모든 활성 시드 전략 로드

    Returns:
        활성 전략 목록 (DSL 형식)

    Raises:
        Exception: DB 연결 또는 조회 실패 시
    """
    from adapter.output.postgresql import PostgresStrategyRepository

    config = _get_db_config()
    repo = PostgresStrategyRepository(**config)

    strategies = await repo.find_all_active()
    logger.info(f"Loaded {len(strategies)} strategies from PostgreSQL")
    return strategies


async def load_strategy_from_db(strategy_id: str) -> Optional[StrategyDefinition]:
    """
    PostgreSQL에서 특정 전략 로드

    Args:
        strategy_id: 전략 ID (예: "golden_cross")

    Returns:
        전략 정의 또는 None (없으면)

    Raises:
        Exception: DB 연결 실패 시
    """
    from adapter.output.postgresql import PostgresStrategyRepository

    config = _get_db_config()
    repo = PostgresStrategyRepository(**config)

    strategy = await repo.find_by_id(strategy_id)
    if strategy:
        logger.debug(f"Loaded strategy {strategy_id} from PostgreSQL")
    else:
        logger.warning(f"Strategy not found: {strategy_id}")
    return strategy


def create_strategy_repository():
    """
    PostgresStrategyRepository 인스턴스 생성

    의존성 주입에 사용.
    """
    from adapter.output.postgresql import PostgresStrategyRepository

    config = _get_db_config()
    return PostgresStrategyRepository(**config)
