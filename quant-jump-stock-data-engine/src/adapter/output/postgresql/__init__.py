"""
PostgreSQL Output Adapters
"""

from .stock_repository import PostgresStockRepository
from .strategy_repository import PostgresStrategyRepository

__all__ = ["PostgresStockRepository", "PostgresStrategyRepository"]
