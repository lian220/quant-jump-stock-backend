"""
Backtest Domain Module

백테스트 관련 도메인 로직을 포함합니다.
- risk_manager: Stop Loss, Take Profit, Trailing Stop 로직
- position_sizer: 포지션 사이징 알고리즘 (Fixed, Volatility, Kelly)
- models: Position, Trade, Portfolio 모델
"""

from .risk_manager import RiskManager, RiskExitResult, RiskExitType
from .position_sizer import PositionSizer, PositionSizingMethod, PositionSizeResult
from .models import Position, Trade, TradeType, Portfolio

__all__ = [
    "RiskManager",
    "RiskExitResult",
    "RiskExitType",
    "PositionSizer",
    "PositionSizingMethod",
    "PositionSizeResult",
    "Position",
    "Trade",
    "TradeType",
    "Portfolio",
]
