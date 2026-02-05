"""
Backtest Result Models

백테스트 실행 결과를 담는 데이터 모델입니다.
"""

from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal
from typing import List, Optional, Dict, Any

from domain.backtest.models import ExitReason


@dataclass
class EquityCurvePoint:
    """수익 곡선 포인트"""
    date: date
    equity: Decimal  # 총 자산
    cash: Decimal    # 현금
    positions_value: Decimal  # 포지션 가치
    drawdown_pct: Decimal  # 고점 대비 낙폭 (%)


@dataclass
class BacktestTrade:
    """
    백테스트 거래 기록

    PostgreSQL backtest_trades 테이블과 매핑
    """
    symbol: str
    trade_type: str  # "buy" or "sell"
    trade_date: date
    price: Decimal
    quantity: int
    amount: Decimal
    commission: Decimal
    exit_reason: Optional[str] = None  # ExitReason.value
    realized_pnl: Optional[Decimal] = None
    realized_pnl_pct: Optional[Decimal] = None
    entry_price: Optional[Decimal] = None
    holding_days: Optional[int] = None


@dataclass
class BacktestResult:
    """
    백테스트 전체 결과

    PostgreSQL backtest_results 테이블과 매핑
    """
    # 기본 정보
    strategy_id: int  # DB PK
    strategy_name: str
    start_date: date
    end_date: date
    initial_capital: Decimal
    final_value: Decimal

    # 성과 지표 (SCRUM-185에서 계산 로직 구현)
    total_return: Decimal = Decimal("0")
    cagr: Decimal = Decimal("0")
    mdd: Decimal = Decimal("0")
    sharpe_ratio: Optional[Decimal] = None
    sortino_ratio: Optional[Decimal] = None
    volatility: Optional[Decimal] = None
    win_rate: Optional[Decimal] = None

    # 거래 통계
    total_trades: int = 0
    winning_trades: int = 0
    losing_trades: int = 0
    avg_win: Optional[Decimal] = None
    avg_loss: Optional[Decimal] = None
    profit_factor: Optional[Decimal] = None
    avg_holding_days: Optional[Decimal] = None

    # 벤치마크 비교
    benchmark_return: Optional[Decimal] = None
    alpha: Optional[Decimal] = None
    beta: Optional[Decimal] = None

    # 상세 데이터
    trades: List[BacktestTrade] = field(default_factory=list)
    equity_curve: List[EquityCurvePoint] = field(default_factory=list)

    # 청산 사유별 통계
    exit_reason_counts: Dict[str, int] = field(default_factory=dict)

    # 메타데이터
    execution_time_seconds: float = 0.0
    error_message: Optional[str] = None
    metadata: Dict[str, Any] = field(default_factory=dict)

    @property
    def is_profitable(self) -> bool:
        """수익 여부"""
        return self.total_return > 0

    @property
    def total_pnl(self) -> Decimal:
        """총 손익 (금액)"""
        return self.final_value - self.initial_capital

    def get_trade_summary(self) -> Dict[str, Any]:
        """거래 요약"""
        return {
            "total_trades": self.total_trades,
            "winning_trades": self.winning_trades,
            "losing_trades": self.losing_trades,
            "win_rate": float(self.win_rate) if self.win_rate else None,
            "avg_win": float(self.avg_win) if self.avg_win else None,
            "avg_loss": float(self.avg_loss) if self.avg_loss else None,
            "profit_factor": float(self.profit_factor) if self.profit_factor else None,
        }

    def get_exit_reason_summary(self) -> Dict[str, int]:
        """청산 사유별 요약"""
        return self.exit_reason_counts

    def to_dict(self) -> Dict[str, Any]:
        """딕셔너리 변환 (API 응답용)"""
        return {
            "strategy_id": self.strategy_id,
            "strategy_name": self.strategy_name,
            "start_date": self.start_date.isoformat(),
            "end_date": self.end_date.isoformat(),
            "initial_capital": float(self.initial_capital),
            "final_value": float(self.final_value),
            "total_return": float(self.total_return),
            "cagr": float(self.cagr),
            "mdd": float(self.mdd),
            "sharpe_ratio": float(self.sharpe_ratio) if self.sharpe_ratio else None,
            "sortino_ratio": float(self.sortino_ratio) if self.sortino_ratio else None,
            "volatility": float(self.volatility) if self.volatility else None,
            "win_rate": float(self.win_rate) if self.win_rate else None,
            "total_trades": self.total_trades,
            "winning_trades": self.winning_trades,
            "losing_trades": self.losing_trades,
            "avg_win": float(self.avg_win) if self.avg_win else None,
            "avg_loss": float(self.avg_loss) if self.avg_loss else None,
            "profit_factor": float(self.profit_factor) if self.profit_factor else None,
            "avg_holding_days": float(self.avg_holding_days) if self.avg_holding_days else None,
            "benchmark_return": float(self.benchmark_return) if self.benchmark_return else None,
            "alpha": float(self.alpha) if self.alpha else None,
            "beta": float(self.beta) if self.beta else None,
            "equity_curve": [
                {
                    "date": p.date.isoformat(),
                    "equity": float(p.equity),
                    "drawdown_pct": float(p.drawdown_pct)
                }
                for p in self.equity_curve
            ],
            "exit_reason_counts": self.exit_reason_counts,
            "execution_time_seconds": self.execution_time_seconds,
        }
