"""
Backtest Domain Models

백테스트에서 사용하는 핵심 도메인 모델입니다.
"""

from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal
from enum import Enum
from typing import Dict, List, Optional


class TradeType(str, Enum):
    """거래 유형"""
    BUY = "buy"
    SELL = "sell"


class ExitReason(str, Enum):
    """청산 사유"""
    STRATEGY_SIGNAL = "strategy_signal"  # 전략 시그널에 의한 매도
    STOP_LOSS = "stop_loss"              # 손절
    TAKE_PROFIT = "take_profit"          # 익절
    TRAILING_STOP = "trailing_stop"      # 트레일링 스탑
    MAX_HOLDING_DAYS = "max_holding_days"  # 최대 보유 기간 초과
    REBALANCE = "rebalance"              # 리밸런싱
    FORCED_EXIT = "forced_exit"          # 강제 청산


@dataclass
class Position:
    """
    개별 포지션 정보

    Attributes:
        symbol: 종목 코드
        entry_date: 진입일
        entry_price: 진입 가격
        quantity: 보유 수량
        current_price: 현재 가격
        highest_price: 보유 기간 중 최고가 (트레일링 스탑용)
        lowest_price: 보유 기간 중 최저가
        cost_basis: 매수 총액 (진입가 × 수량)
    """
    symbol: str
    entry_date: date
    entry_price: Decimal
    quantity: int
    current_price: Decimal = Decimal("0")
    highest_price: Decimal = Decimal("0")
    lowest_price: Decimal = Decimal("0")
    cost_basis: Decimal = Decimal("0")

    def __post_init__(self):
        """초기화 후 처리"""
        if self.highest_price == 0:
            self.highest_price = self.entry_price
        if self.lowest_price == 0:
            self.lowest_price = self.entry_price
        if self.current_price == 0:
            self.current_price = self.entry_price
        if self.cost_basis == 0:
            self.cost_basis = self.entry_price * self.quantity

    @property
    def pnl(self) -> Decimal:
        """미실현 손익 (금액)"""
        return (self.current_price - self.entry_price) * self.quantity

    @property
    def pnl_pct(self) -> Decimal:
        """미실현 손익률 (%)"""
        if self.entry_price == 0:
            return Decimal("0")
        return ((self.current_price / self.entry_price) - 1) * 100

    @property
    def market_value(self) -> Decimal:
        """현재 시장 가치"""
        return self.current_price * self.quantity

    @property
    def drawdown_from_high(self) -> Decimal:
        """고점 대비 하락률 (%)"""
        if self.highest_price == 0:
            return Decimal("0")
        return ((self.current_price / self.highest_price) - 1) * 100

    def update_price(self, new_price: Decimal) -> None:
        """가격 업데이트 및 최고/최저가 갱신"""
        self.current_price = new_price
        if new_price > self.highest_price:
            self.highest_price = new_price
        if new_price < self.lowest_price:
            self.lowest_price = new_price

    def holding_days(self, current_date: date) -> int:
        """보유 일수"""
        return (current_date - self.entry_date).days


@dataclass
class Trade:
    """
    거래 기록

    Attributes:
        symbol: 종목 코드
        trade_type: 거래 유형 (buy/sell)
        trade_date: 거래일
        price: 거래 가격
        quantity: 거래 수량
        amount: 거래 금액
        commission: 수수료
        exit_reason: 청산 사유 (매도 시에만)
        realized_pnl: 실현 손익 (매도 시에만)
        realized_pnl_pct: 실현 손익률 (매도 시에만)
    """
    symbol: str
    trade_type: TradeType
    trade_date: date
    price: Decimal
    quantity: int
    amount: Decimal = Decimal("0")
    commission: Decimal = Decimal("0")
    exit_reason: Optional[ExitReason] = None
    realized_pnl: Optional[Decimal] = None
    realized_pnl_pct: Optional[Decimal] = None
    entry_price: Optional[Decimal] = None  # 매도 시 원래 진입가

    def __post_init__(self):
        if self.amount == 0:
            self.amount = self.price * self.quantity


@dataclass
class Portfolio:
    """
    포트폴리오 상태

    Attributes:
        cash: 현금 잔고
        positions: 보유 포지션 (symbol -> Position)
        trades: 거래 기록
        initial_capital: 초기 자본
        current_date: 현재 날짜
    """
    initial_capital: Decimal
    cash: Decimal = Decimal("0")
    positions: Dict[str, Position] = field(default_factory=dict)
    trades: List[Trade] = field(default_factory=list)
    current_date: Optional[date] = None

    def __post_init__(self):
        if self.cash == 0:
            self.cash = self.initial_capital

    @property
    def total_value(self) -> Decimal:
        """총 자산 가치 (현금 + 포지션 시장가치)"""
        position_value = sum(p.market_value for p in self.positions.values())
        return self.cash + position_value

    @property
    def total_pnl(self) -> Decimal:
        """총 손익 (금액)"""
        return self.total_value - self.initial_capital

    @property
    def total_pnl_pct(self) -> Decimal:
        """총 수익률 (%)"""
        if self.initial_capital == 0:
            return Decimal("0")
        return ((self.total_value / self.initial_capital) - 1) * 100

    @property
    def position_count(self) -> int:
        """보유 포지션 수"""
        return len(self.positions)

    def get_position_weight(self, symbol: str) -> Decimal:
        """특정 포지션의 포트폴리오 비중 (%)"""
        if self.total_value == 0:
            return Decimal("0")
        position = self.positions.get(symbol)
        if not position:
            return Decimal("0")
        return (position.market_value / self.total_value) * 100

    def open_position(
        self,
        symbol: str,
        trade_date: date,
        price: Decimal,
        quantity: int,
        commission: Decimal = Decimal("0")
    ) -> Optional[Trade]:
        """
        새 포지션 오픈 (매수)

        Returns:
            Trade 기록, 또는 None (자금 부족 시)
        """
        amount = price * quantity + commission

        if amount > self.cash:
            return None  # 자금 부족

        # 현금 차감
        self.cash -= amount

        # 포지션 생성/추가
        if symbol in self.positions:
            # 기존 포지션에 추가 (평균 단가 계산)
            existing = self.positions[symbol]
            total_quantity = existing.quantity + quantity
            total_cost = existing.cost_basis + (price * quantity)
            avg_price = total_cost / total_quantity

            existing.entry_price = avg_price
            existing.quantity = total_quantity
            existing.cost_basis = total_cost
        else:
            # 새 포지션
            self.positions[symbol] = Position(
                symbol=symbol,
                entry_date=trade_date,
                entry_price=price,
                quantity=quantity,
                current_price=price
            )

        # 거래 기록
        trade = Trade(
            symbol=symbol,
            trade_type=TradeType.BUY,
            trade_date=trade_date,
            price=price,
            quantity=quantity,
            commission=commission
        )
        self.trades.append(trade)

        return trade

    def close_position(
        self,
        symbol: str,
        trade_date: date,
        price: Decimal,
        quantity: Optional[int] = None,
        exit_reason: ExitReason = ExitReason.STRATEGY_SIGNAL,
        commission: Decimal = Decimal("0")
    ) -> Optional[Trade]:
        """
        포지션 청산 (매도)

        Args:
            symbol: 종목 코드
            trade_date: 거래일
            price: 매도 가격
            quantity: 매도 수량 (None이면 전량 매도)
            exit_reason: 청산 사유
            commission: 수수료

        Returns:
            Trade 기록, 또는 None (포지션 없음)
        """
        position = self.positions.get(symbol)
        if not position:
            return None

        # 매도 수량 결정
        sell_quantity = quantity if quantity is not None else position.quantity
        sell_quantity = min(sell_quantity, position.quantity)

        # 실현 손익 계산
        amount = price * sell_quantity - commission
        cost_basis_sold = position.entry_price * sell_quantity
        realized_pnl = amount - cost_basis_sold
        realized_pnl_pct = ((price / position.entry_price) - 1) * 100

        # 현금 추가
        self.cash += amount

        # 포지션 업데이트
        if sell_quantity >= position.quantity:
            # 전량 청산
            del self.positions[symbol]
        else:
            # 부분 청산
            position.quantity -= sell_quantity
            position.cost_basis = position.entry_price * position.quantity

        # 거래 기록
        trade = Trade(
            symbol=symbol,
            trade_type=TradeType.SELL,
            trade_date=trade_date,
            price=price,
            quantity=sell_quantity,
            commission=commission,
            exit_reason=exit_reason,
            realized_pnl=realized_pnl,
            realized_pnl_pct=realized_pnl_pct,
            entry_price=position.entry_price
        )
        self.trades.append(trade)

        return trade

    def update_prices(self, prices: Dict[str, Decimal]) -> None:
        """모든 포지션의 가격 업데이트"""
        for symbol, position in self.positions.items():
            if symbol in prices:
                position.update_price(prices[symbol])

    def get_realized_trades(self) -> List[Trade]:
        """매도 거래만 반환"""
        return [t for t in self.trades if t.trade_type == TradeType.SELL]

    def get_trades_by_reason(self, reason: ExitReason) -> List[Trade]:
        """특정 사유의 거래만 반환"""
        return [t for t in self.trades if t.exit_reason == reason]

    def get_completed_trades(self) -> list:
        """
        BUY/SELL 매칭하여 CompletedTrade 리스트 생성 (FIFO)

        부분 청산을 지원합니다: BUY 100 → SELL 50 → SELL 50 시
        BUY 레코드를 수량 단위로 분할하여 매칭합니다.

        Returns:
            CompletedTrade 객체 리스트
        """
        from domain.backtest.metrics import CompletedTrade
        from typing import Tuple

        # (entry_date, entry_price, remaining_qty) 큐
        buy_queue: Dict[str, List[list]] = {}
        completed = []

        for t in self.trades:
            if t.trade_type == TradeType.BUY:
                buy_queue.setdefault(t.symbol, []).append(
                    [t.trade_date, t.price, t.quantity]
                )
            elif t.trade_type == TradeType.SELL and t.realized_pnl is not None:
                remaining = t.quantity
                queue = buy_queue.get(t.symbol, [])

                while remaining > 0 and queue:
                    b_date, b_price, b_qty = queue[0]
                    matched = min(remaining, b_qty)

                    if matched == b_qty:
                        queue.pop(0)
                    else:
                        queue[0][2] = b_qty - matched

                    ratio = Decimal(str(matched)) / Decimal(str(t.quantity))
                    completed.append(CompletedTrade(
                        symbol=t.symbol,
                        entry_date=b_date,
                        exit_date=t.trade_date,
                        entry_price=b_price,
                        exit_price=t.price,
                        quantity=matched,
                        pnl=t.realized_pnl * ratio,
                        pnl_pct=t.realized_pnl_pct or Decimal("0"),
                        exit_reason=t.exit_reason.value if t.exit_reason else "signal",
                    ))
                    remaining -= matched

                # BUY 매칭 없는 경우 (fallback)
                if remaining > 0:
                    completed.append(CompletedTrade(
                        symbol=t.symbol,
                        entry_date=t.trade_date,
                        exit_date=t.trade_date,
                        entry_price=t.entry_price or t.price,
                        exit_price=t.price,
                        quantity=remaining,
                        pnl=t.realized_pnl * Decimal(str(remaining)) / Decimal(str(t.quantity)),
                        pnl_pct=t.realized_pnl_pct or Decimal("0"),
                        exit_reason=t.exit_reason.value if t.exit_reason else "signal",
                    ))

        return completed
