"""
Backtest Domain Module

백테스트 관련 도메인 로직을 포함합니다.
- risk_manager: Stop Loss, Take Profit, Trailing Stop 로직
- models: Position, Trade, Portfolio 모델
- metrics: 성과 지표 계산 (CAGR, MDD, Sharpe, Profit Factor, Kelly 등)
"""

from .risk_manager import RiskManager, RiskExitResult, RiskExitType
from .models import Position, Trade, TradeType, Portfolio
from .metrics import (
    MetricsCalculator,
    PerformanceMetrics,
    TradeAnalysis,
)

__all__ = [
    # Risk Manager
    "RiskManager",
    "RiskExitResult",
    "RiskExitType",
    # Models
    "Position",
    "Trade",
    "TradeType",
    "Portfolio",
    # Metrics
    "MetricsCalculator",
    "PerformanceMetrics",
    "TradeAnalysis",
]
