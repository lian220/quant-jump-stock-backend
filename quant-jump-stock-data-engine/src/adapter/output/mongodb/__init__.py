"""
MongoDB Output Adapter

MongoDB 영속성 어댑터.
"""

from .strategy_repository import MongoStrategyRepository
from .signal_repository import MongoSignalRepository

__all__ = [
    "MongoStrategyRepository",
    "MongoSignalRepository",
]
