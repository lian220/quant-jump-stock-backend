"""
Portfolio Simulation Module

포트폴리오 시뮬레이션을 위한 모듈입니다.
PerformanceMetrics 클래스를 제공합니다.

Note:
    Position, Trade, TradeType, Portfolio 모델은 domain.backtest.models에서 import하세요.
    BacktestEngine과 BacktestResult는 application.backtest.engine과 result에서 import하세요.
"""

import logging
from dataclasses import dataclass
from typing import Dict

logger = logging.getLogger(__name__)


# Re-export domain models for backward compatibility
from domain.backtest.models import (
    Position,
    Trade,
    TradeType,
    Portfolio,
    ExitReason,
)


@dataclass
class PerformanceMetrics:
    """
    성과 지표

    백테스트 결과의 성과를 분석합니다.
    """
    total_return: float = 0.0
    total_return_pct: float = 0.0
    annualized_return: float = 0.0
    volatility: float = 0.0
    sharpe_ratio: float = 0.0
    sortino_ratio: float = 0.0
    max_drawdown: float = 0.0
    max_drawdown_pct: float = 0.0
    win_rate: float = 0.0
    profit_factor: float = 0.0
    total_trades: int = 0
    winning_trades: int = 0
    losing_trades: int = 0
    average_win: float = 0.0
    average_loss: float = 0.0
    largest_win: float = 0.0
    largest_loss: float = 0.0
    average_holding_period: float = 0.0  # days

    def to_dict(self) -> Dict:
        """딕셔너리로 변환"""
        return {
            "total_return": self.total_return,
            "total_return_pct": self.total_return_pct,
            "annualized_return": self.annualized_return,
            "volatility": self.volatility,
            "sharpe_ratio": self.sharpe_ratio,
            "sortino_ratio": self.sortino_ratio,
            "max_drawdown": self.max_drawdown,
            "max_drawdown_pct": self.max_drawdown_pct,
            "win_rate": self.win_rate,
            "profit_factor": self.profit_factor,
            "total_trades": self.total_trades,
            "winning_trades": self.winning_trades,
            "losing_trades": self.losing_trades,
            "average_win": self.average_win,
            "average_loss": self.average_loss,
            "largest_win": self.largest_win,
            "largest_loss": self.largest_loss,
            "average_holding_period": self.average_holding_period,
        }
