"""
Application Layer

Hexagonal Architecture의 Application 레이어.
유스케이스와 포트를 통해 도메인과 외부 세계를 연결.
"""

from .ports import (
    # Output Ports
    StrategyRepositoryPort,
    SignalOutputPort,
    NotificationPort,
    # Input Ports
    StrategyExecutionUseCase,
    SignalGenerationUseCase,
)

from .strategy import (
    StrategyExecutor,
    StrategyService,
)

from .backtest import (
    Portfolio,
    BacktestEngine,
    BacktestResult,
    Position,
    Trade,
    TradeType,
)
from domain.backtest.metrics import PerformanceMetrics

__all__ = [
    # Output Ports
    "StrategyRepositoryPort",
    "SignalOutputPort",
    "NotificationPort",
    # Input Ports
    "StrategyExecutionUseCase",
    "SignalGenerationUseCase",
    # Services
    "StrategyExecutor",
    "StrategyService",
    # Backtest
    "Portfolio",
    "BacktestEngine",
    "BacktestResult",
    "PerformanceMetrics",
    "Position",
    "Trade",
    "TradeType",
]
