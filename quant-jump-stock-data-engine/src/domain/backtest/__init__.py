"""
Backtest Domain Module

백테스트 관련 도메인 로직을 포함합니다.
- risk_manager: Stop Loss, Take Profit, Trailing Stop 로직
- models: Position, Trade, Portfolio 모델
"""

from .risk_manager import RiskManager, RiskExitResult, RiskExitType
from .models import Position, Trade, TradeType, Portfolio

__all__ = [
    "RiskManager",
    "RiskExitResult",
    "RiskExitType",
    "Position",
    "Trade",
    "TradeType",
    "Portfolio",
]
