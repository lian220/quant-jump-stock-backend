"""
Backtest Domain Module

백테스트 관련 도메인 로직을 포함합니다.
- metrics: 성과 지표 계산 (CAGR, MDD, Sharpe, Profit Factor, Kelly 등)
"""

from .metrics import (
    MetricsCalculator,
    PerformanceMetrics,
    Trade,
    TradeAnalysis,
)

__all__ = [
    "MetricsCalculator",
    "PerformanceMetrics",
    "Trade",
    "TradeAnalysis",
]
