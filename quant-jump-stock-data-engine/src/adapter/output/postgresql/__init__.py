"""
PostgreSQL Output Adapters
"""

from .stock_repository import PostgresStockRepository
from .strategy_repository import PostgresStrategyRepository
from .backtest_repository import PostgresBacktestRepository

__all__ = [
    "PostgresStockRepository",
    "PostgresStrategyRepository",
    "PostgresBacktestRepository",
]
