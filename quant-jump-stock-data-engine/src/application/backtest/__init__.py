"""
Backtest Application Module

백테스트 실행 및 결과 관리를 위한 애플리케이션 레이어.
포트폴리오 시뮬레이션, 성과 지표 계산 기능을 제공합니다.
"""

from .engine import BacktestEngine, BacktestConfig
from .result import BacktestResult, BacktestTrade, EquityCurvePoint
from .data_loader import DataLoader, YFinanceDataLoader
from .portfolio import (
    Position,
    Trade,
    TradeType,
    Portfolio,
    PerformanceMetrics,
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
    # Portfolio (from SCRUM-183)
    "Position",
    "Trade",
    "TradeType",
    "Portfolio",
    "PerformanceMetrics",
]
