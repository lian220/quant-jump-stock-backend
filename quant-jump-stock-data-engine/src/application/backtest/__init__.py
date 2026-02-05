"""
Backtest Application Module

백테스트 실행 및 결과 관리를 위한 애플리케이션 레이어.
포트폴리오 시뮬레이션, 성과 지표 계산 기능을 제공합니다.
"""

from .engine import BacktestEngine, BacktestConfig
from .result import BacktestResult, BacktestTrade, EquityCurvePoint
from .data_loader import DataLoader, YFinanceDataLoader, PandasDataLoader
from .data_loader_mongo import MongoDataLoader
from .portfolio import PerformanceMetrics

# Re-export domain models for convenience
from domain.backtest.models import (
    Position,
    Trade,
    TradeType,
    Portfolio,
    ExitReason,
)

__all__ = [
    # Engine
    "BacktestEngine",
    "BacktestConfig",
    # Result
    "BacktestResult",
    "BacktestTrade",
    "EquityCurvePoint",
    # Data Loader
    "DataLoader",
    "YFinanceDataLoader",
    "PandasDataLoader",
    # Portfolio Domain Models (from domain.backtest)
    "Position",
    "Trade",
    "TradeType",
    "Portfolio",
    "ExitReason",
    # Performance
    "PerformanceMetrics",
]
