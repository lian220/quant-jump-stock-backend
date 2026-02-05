"""
Backtest Domain Module

백테스트 관련 도메인 로직을 포함합니다.
- trading_cost: 거래 비용 계산 (수수료, 세금, 슬리피지)
"""

from .trading_cost import (
    TradingCostCalculator,
    TradeCostResult,
    PnLResult,
    SlippageConfig,
    TradeDirection,
)

__all__ = [
    "TradingCostCalculator",
    "TradeCostResult",
    "PnLResult",
    "SlippageConfig",
    "TradeDirection",
]
