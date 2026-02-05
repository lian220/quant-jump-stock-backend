"""
Backtest Domain Models

백테스트 실행 및 결과 분석을 위한 도메인 모델.
"""

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import List, Optional, Dict, Any


class TradeType(str, Enum):
    """거래 유형"""
    BUY = "buy"
    SELL = "sell"


@dataclass
class Trade:
    """
    개별 거래 기록

    백테스트 중 발생한 매수/매도 거래를 기록합니다.
    """
    trade_id: str
    symbol: str
    trade_type: TradeType
    quantity: int
    price: float
    timestamp: datetime
    rule_name: Optional[str] = None
    commission: float = 0.0
    slippage: float = 0.0

    @property
    def total_value(self) -> float:
        """총 거래 금액 (수수료/슬리피지 제외)"""
        return self.quantity * self.price

    @property
    def total_cost(self) -> float:
        """총 비용 (수수료/슬리피지 포함)"""
        if self.trade_type == TradeType.BUY:
            return self.total_value + self.commission + self.slippage
        else:
            return self.total_value - self.commission - self.slippage


@dataclass
class Position:
    """
    포지션 상태

    특정 종목에 대한 현재 보유 상태를 나타냅니다.
    """
    symbol: str
    quantity: int = 0
    avg_price: float = 0.0
    entry_timestamp: Optional[datetime] = None
    unrealized_pnl: float = 0.0
    realized_pnl: float = 0.0

    @property
    def is_open(self) -> bool:
        """포지션이 열려있는지 여부"""
        return self.quantity > 0

    @property
    def market_value(self) -> float:
        """현재 시장 가치"""
        return self.quantity * self.avg_price

    def update_unrealized_pnl(self, current_price: float) -> None:
        """미실현 손익 업데이트"""
        if self.quantity > 0:
            self.unrealized_pnl = (current_price - self.avg_price) * self.quantity
        else:
            self.unrealized_pnl = 0.0


@dataclass
class BacktestConfig:
    """
    백테스트 설정

    백테스트 실행에 필요한 설정 값들을 정의합니다.
    """
    initial_capital: float = 10_000_000.0  # 초기 자본금 (1000만원)
    commission_rate: float = 0.00015       # 수수료율 (0.015%)
    slippage_rate: float = 0.0001          # 슬리피지율 (0.01%)
    max_position_size: float = 0.1         # 최대 포지션 크기 (자본의 10%)
    stop_loss_pct: float = 0.05            # 손절 비율 (5%)
    take_profit_pct: float = 0.15          # 익절 비율 (15%)
    allow_short: bool = False              # 공매도 허용 여부
    reinvest_profits: bool = True          # 수익 재투자 여부

    def validate(self) -> None:
        """설정 값 검증"""
        if self.initial_capital <= 0:
            raise ValueError("Initial capital must be positive")
        if not 0 <= self.commission_rate <= 0.01:
            raise ValueError("Commission rate must be between 0 and 1%")
        if not 0 <= self.max_position_size <= 1:
            raise ValueError("Max position size must be between 0 and 100%")


@dataclass
class EquityPoint:
    """자산 곡선 데이터 포인트"""
    timestamp: datetime
    equity: float
    cash: float
    positions_value: float
    drawdown: float = 0.0
    drawdown_pct: float = 0.0


@dataclass
class PerformanceMetrics:
    """
    성과 지표

    백테스트 결과에서 계산된 성과 지표들입니다.
    """
    # 수익률 지표
    total_return: float = 0.0              # 총 수익률 (%)
    annualized_return: float = 0.0         # 연환산 수익률 (%)

    # 위험 지표
    max_drawdown: float = 0.0              # 최대 낙폭 (%)
    max_drawdown_duration_days: int = 0    # 최대 낙폭 지속 기간 (일)
    volatility: float = 0.0                # 변동성 (연환산)

    # 리스크 대비 수익 지표
    sharpe_ratio: float = 0.0              # 샤프 비율
    sortino_ratio: float = 0.0             # 소르티노 비율
    calmar_ratio: float = 0.0              # 칼마 비율

    # 거래 통계
    total_trades: int = 0                  # 총 거래 수
    winning_trades: int = 0                # 이긴 거래 수
    losing_trades: int = 0                 # 진 거래 수
    win_rate: float = 0.0                  # 승률 (%)

    # 손익 통계
    avg_win: float = 0.0                   # 평균 이익
    avg_loss: float = 0.0                  # 평균 손실
    profit_factor: float = 0.0             # 손익비
    avg_trade_return: float = 0.0          # 평균 거래 수익률

    # 기간 정보
    trading_days: int = 0                  # 거래일 수
    start_date: Optional[datetime] = None
    end_date: Optional[datetime] = None


@dataclass
class BacktestResult:
    """
    백테스트 결과

    백테스트 실행 후 생성되는 전체 결과입니다.
    """
    # 식별 정보
    backtest_id: str
    strategy_id: str
    strategy_name: str
    symbol: str

    # 설정
    config: BacktestConfig

    # 결과 요약
    initial_capital: float
    final_capital: float
    total_pnl: float
    total_return_pct: float

    # 상세 데이터
    trades: List[Trade] = field(default_factory=list)
    equity_curve: List[EquityPoint] = field(default_factory=list)

    # 성과 지표
    metrics: PerformanceMetrics = field(default_factory=PerformanceMetrics)

    # 메타데이터
    executed_at: datetime = field(default_factory=datetime.utcnow)
    execution_time_ms: float = 0.0
    errors: List[str] = field(default_factory=list)
    warnings: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        """딕셔너리로 변환 (직렬화용)"""
        return {
            "backtest_id": self.backtest_id,
            "strategy_id": self.strategy_id,
            "strategy_name": self.strategy_name,
            "symbol": self.symbol,
            "initial_capital": self.initial_capital,
            "final_capital": self.final_capital,
            "total_pnl": self.total_pnl,
            "total_return_pct": self.total_return_pct,
            "metrics": {
                "total_return": self.metrics.total_return,
                "annualized_return": self.metrics.annualized_return,
                "max_drawdown": self.metrics.max_drawdown,
                "sharpe_ratio": self.metrics.sharpe_ratio,
                "sortino_ratio": self.metrics.sortino_ratio,
                "total_trades": self.metrics.total_trades,
                "win_rate": self.metrics.win_rate,
                "profit_factor": self.metrics.profit_factor,
            },
            "trade_count": len(self.trades),
            "executed_at": self.executed_at.isoformat(),
            "execution_time_ms": self.execution_time_ms,
        }

    @property
    def is_profitable(self) -> bool:
        """수익 여부"""
        return self.total_pnl > 0
