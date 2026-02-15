"""
Backtest Domain Module

백테스트 관련 도메인 로직을 포함합니다.
- risk_manager: Stop Loss, Take Profit, Trailing Stop 로직
- models: Position, Trade, Portfolio 모델
- metrics: 성과 지표 계산 (CAGR, MDD, Sharpe, Profit Factor, Kelly 등)
- position_sizer: 포지션 사이징 (고정비율, 켈리, 변동성 타겟 등)
- trading_costs: 거래 비용 (수수료, 세금, 슬리피지)
"""

from .risk_manager import RiskManager, RiskExitResult, RiskExitType
from .models import Position, Trade, TradeType, ExitReason, Portfolio
from .metrics import (
    MetricsCalculator,
    CompletedTrade,
    PerformanceMetrics,
    TradeAnalysis,
)
from .position_sizer import PositionSizer
from .trading_costs import TradingCostCalculator, TradingCostBreakdown

__all__ = [
    # Risk Manager
    "RiskManager",
    "RiskExitResult",
    "RiskExitType",
    # Models
    "Position",
    "Trade",
    "TradeType",
    "ExitReason",
    "Portfolio",
    # Metrics
    "MetricsCalculator",
    "CompletedTrade",
    "PerformanceMetrics",
    "TradeAnalysis",
    # Position Sizing
    "PositionSizer",
    # Trading Costs
    "TradingCostCalculator",
    "TradingCostBreakdown",
]
