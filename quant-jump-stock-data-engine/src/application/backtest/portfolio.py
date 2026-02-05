"""
Portfolio Simulation Module

포트폴리오 시뮬레이션을 위한 핵심 모듈입니다.
백테스트 엔진, 포트폴리오 관리, 성과 지표 계산 기능을 제공합니다.
"""

import logging
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Dict, List, Optional, Tuple
import math

import numpy as np
import pandas as pd

from domain.strategy.models import StrategyDefinition, SignalType, RiskManagement
from domain.common.exceptions import (
    DomainException,
    InsufficientDataError,
    RiskLimitExceededError,
)

logger = logging.getLogger(__name__)


class TradeType(str, Enum):
    """거래 유형"""
    BUY = "buy"
    SELL = "sell"


@dataclass
class Trade:
    """
    거래 기록

    개별 매수/매도 거래를 기록합니다.
    """
    trade_id: str
    symbol: str
    trade_type: TradeType
    quantity: int
    price: float
    timestamp: datetime
    commission: float = 0.0
    slippage: float = 0.0
    strategy_id: Optional[str] = None
    rule_name: Optional[str] = None

    @property
    def total_cost(self) -> float:
        """총 거래 비용 (수수료 + 슬리피지 포함)"""
        base_cost = self.quantity * self.price
        if self.trade_type == TradeType.BUY:
            return base_cost + self.commission + self.slippage
        else:
            return base_cost - self.commission - self.slippage

    @property
    def net_amount(self) -> float:
        """순 거래 금액 (매수: 음수, 매도: 양수)"""
        if self.trade_type == TradeType.BUY:
            return -self.total_cost
        else:
            return self.total_cost


@dataclass
class Position:
    """
    포지션 (보유 종목)

    개별 종목에 대한 보유 정보를 관리합니다.
    """
    symbol: str
    quantity: int
    average_cost: float
    entry_date: datetime
    entry_price: float
    highest_price: float = 0.0  # 트레일링 스탑용

    @property
    def total_cost(self) -> float:
        """총 매입 비용"""
        return self.quantity * self.average_cost

    def current_value(self, current_price: float) -> float:
        """현재 평가 금액"""
        return self.quantity * current_price

    def unrealized_pnl(self, current_price: float) -> float:
        """미실현 손익"""
        return self.current_value(current_price) - self.total_cost

    def unrealized_pnl_pct(self, current_price: float) -> float:
        """미실현 손익률"""
        if self.total_cost == 0:
            return 0.0
        return (self.unrealized_pnl(current_price) / self.total_cost) * 100

    def update_highest_price(self, current_price: float) -> None:
        """최고가 갱신 (트레일링 스탑용)"""
        if current_price > self.highest_price:
            self.highest_price = current_price

    def add_quantity(self, quantity: int, price: float) -> None:
        """수량 추가 (평균 단가 재계산)"""
        total_cost = self.total_cost + (quantity * price)
        self.quantity += quantity
        self.average_cost = total_cost / self.quantity if self.quantity > 0 else 0.0

    def reduce_quantity(self, quantity: int) -> int:
        """수량 감소, 실제 감소량 반환"""
        actual_reduce = min(quantity, self.quantity)
        self.quantity -= actual_reduce
        return actual_reduce


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


@dataclass
class BacktestResult:
    """
    백테스트 결과

    백테스트 실행 결과를 담습니다.
    """
    strategy_id: str
    start_date: datetime
    end_date: datetime
    initial_capital: float
    final_capital: float
    metrics: PerformanceMetrics
    trades: List[Trade]
    equity_curve: pd.DataFrame  # date, equity, drawdown
    positions_history: List[Dict]  # 포지션 변화 기록

    def to_dict(self) -> Dict:
        """딕셔너리로 변환"""
        return {
            "strategy_id": self.strategy_id,
            "start_date": self.start_date.isoformat(),
            "end_date": self.end_date.isoformat(),
            "initial_capital": self.initial_capital,
            "final_capital": self.final_capital,
            "metrics": self.metrics.to_dict(),
            "total_trades": len(self.trades),
        }


class Portfolio:
    """
    포트폴리오 관리

    현금과 보유 포지션을 관리하고 거래를 실행합니다.
    """

    def __init__(
        self,
        initial_capital: float,
        commission_rate: float = 0.00015,  # 0.015% (한국 주식 기준)
        slippage_rate: float = 0.001,  # 0.1%
        risk_management: Optional[RiskManagement] = None,
    ):
        """
        Args:
            initial_capital: 초기 자본금
            commission_rate: 수수료율 (기본 0.015%)
            slippage_rate: 슬리피지율 (기본 0.1%)
            risk_management: 리스크 관리 설정
        """
        self.initial_capital = initial_capital
        self.cash = initial_capital
        self.commission_rate = commission_rate
        self.slippage_rate = slippage_rate
        self.risk_management = risk_management or RiskManagement()

        self.positions: Dict[str, Position] = {}
        self.trades: List[Trade] = []
        self.equity_history: List[Tuple[datetime, float]] = []
        self._trade_counter = 0
        self._peak_equity = initial_capital

    @property
    def total_equity(self) -> float:
        """총 자산 (현금 + 포지션 평가액)"""
        # 포지션 평가를 위해서는 현재가가 필요하므로 cash만 반환
        # 정확한 계산은 calculate_equity() 메서드 사용
        return self.cash

    def calculate_equity(self, current_prices: Dict[str, float]) -> float:
        """
        총 자산 계산

        Args:
            current_prices: 종목별 현재가 딕셔너리

        Returns:
            총 자산 (현금 + 포지션 평가액)
        """
        equity = self.cash
        for symbol, position in self.positions.items():
            if symbol in current_prices:
                equity += position.current_value(current_prices[symbol])
            else:
                # 현재가가 없으면 평균 단가로 계산
                equity += position.total_cost
        return equity

    def calculate_drawdown(self, current_equity: float) -> Tuple[float, float]:
        """
        드로우다운 계산

        Args:
            current_equity: 현재 자산

        Returns:
            (드로우다운 금액, 드로우다운 비율)
        """
        if current_equity > self._peak_equity:
            self._peak_equity = current_equity

        drawdown = self._peak_equity - current_equity
        drawdown_pct = (drawdown / self._peak_equity) * 100 if self._peak_equity > 0 else 0.0

        return drawdown, drawdown_pct

    def can_buy(
        self,
        symbol: str,
        quantity: int,
        price: float,
        current_equity: float,
    ) -> Tuple[bool, str]:
        """
        매수 가능 여부 확인

        Args:
            symbol: 종목 코드
            quantity: 매수 수량
            price: 매수 가격
            current_equity: 현재 총 자산

        Returns:
            (가능 여부, 불가 사유)
        """
        total_cost = quantity * price * (1 + self.commission_rate + self.slippage_rate)

        # 현금 확인
        if total_cost > self.cash:
            return False, f"Insufficient cash: need {total_cost:.2f}, have {self.cash:.2f}"

        # 최대 포지션 비율 확인
        max_position_value = current_equity * self.risk_management.max_position_pct
        existing_value = 0.0
        if symbol in self.positions:
            existing_value = self.positions[symbol].quantity * price

        new_position_value = existing_value + (quantity * price)
        if new_position_value > max_position_value:
            return False, f"Position limit exceeded: {new_position_value:.2f} > {max_position_value:.2f}"

        return True, ""

    def execute_buy(
        self,
        symbol: str,
        quantity: int,
        price: float,
        timestamp: datetime,
        strategy_id: Optional[str] = None,
        rule_name: Optional[str] = None,
    ) -> Optional[Trade]:
        """
        매수 실행

        Args:
            symbol: 종목 코드
            quantity: 매수 수량
            price: 매수 가격
            timestamp: 거래 시각
            strategy_id: 전략 ID
            rule_name: 규칙 이름

        Returns:
            Trade 객체 (실패 시 None)
        """
        commission = quantity * price * self.commission_rate
        slippage = quantity * price * self.slippage_rate
        total_cost = (quantity * price) + commission + slippage

        if total_cost > self.cash:
            logger.warning(f"Insufficient cash for buy: {symbol}, need {total_cost:.2f}")
            return None

        # 현금 차감
        self.cash -= total_cost

        # 포지션 업데이트
        if symbol in self.positions:
            self.positions[symbol].add_quantity(quantity, price)
        else:
            self.positions[symbol] = Position(
                symbol=symbol,
                quantity=quantity,
                average_cost=price,
                entry_date=timestamp,
                entry_price=price,
                highest_price=price,
            )

        # 거래 기록
        self._trade_counter += 1
        trade = Trade(
            trade_id=f"T{self._trade_counter:06d}",
            symbol=symbol,
            trade_type=TradeType.BUY,
            quantity=quantity,
            price=price,
            timestamp=timestamp,
            commission=commission,
            slippage=slippage,
            strategy_id=strategy_id,
            rule_name=rule_name,
        )
        self.trades.append(trade)

        logger.debug(
            f"BUY executed: {symbol} x{quantity} @ {price:.2f}, "
            f"total: {total_cost:.2f}, cash: {self.cash:.2f}"
        )

        return trade

    def execute_sell(
        self,
        symbol: str,
        quantity: int,
        price: float,
        timestamp: datetime,
        strategy_id: Optional[str] = None,
        rule_name: Optional[str] = None,
    ) -> Optional[Trade]:
        """
        매도 실행

        Args:
            symbol: 종목 코드
            quantity: 매도 수량
            price: 매도 가격
            timestamp: 거래 시각
            strategy_id: 전략 ID
            rule_name: 규칙 이름

        Returns:
            Trade 객체 (실패 시 None)
        """
        if symbol not in self.positions:
            logger.warning(f"No position to sell: {symbol}")
            return None

        position = self.positions[symbol]
        actual_quantity = min(quantity, position.quantity)

        if actual_quantity <= 0:
            logger.warning(f"No quantity to sell: {symbol}")
            return None

        commission = actual_quantity * price * self.commission_rate
        slippage = actual_quantity * price * self.slippage_rate
        proceeds = (actual_quantity * price) - commission - slippage

        # 현금 추가
        self.cash += proceeds

        # 포지션 업데이트
        position.reduce_quantity(actual_quantity)
        if position.quantity <= 0:
            del self.positions[symbol]

        # 거래 기록
        self._trade_counter += 1
        trade = Trade(
            trade_id=f"T{self._trade_counter:06d}",
            symbol=symbol,
            trade_type=TradeType.SELL,
            quantity=actual_quantity,
            price=price,
            timestamp=timestamp,
            commission=commission,
            slippage=slippage,
            strategy_id=strategy_id,
            rule_name=rule_name,
        )
        self.trades.append(trade)

        logger.debug(
            f"SELL executed: {symbol} x{actual_quantity} @ {price:.2f}, "
            f"proceeds: {proceeds:.2f}, cash: {self.cash:.2f}"
        )

        return trade

    def check_stop_loss(
        self,
        symbol: str,
        current_price: float,
    ) -> bool:
        """
        손절 조건 확인

        Args:
            symbol: 종목 코드
            current_price: 현재가

        Returns:
            손절 필요 여부
        """
        if symbol not in self.positions:
            return False

        position = self.positions[symbol]
        loss_pct = -position.unrealized_pnl_pct(current_price) / 100

        return loss_pct >= self.risk_management.stop_loss_pct

    def check_take_profit(
        self,
        symbol: str,
        current_price: float,
    ) -> bool:
        """
        익절 조건 확인

        Args:
            symbol: 종목 코드
            current_price: 현재가

        Returns:
            익절 필요 여부
        """
        if symbol not in self.positions:
            return False

        position = self.positions[symbol]
        profit_pct = position.unrealized_pnl_pct(current_price) / 100

        return profit_pct >= self.risk_management.take_profit_pct

    def check_trailing_stop(
        self,
        symbol: str,
        current_price: float,
    ) -> bool:
        """
        트레일링 스탑 조건 확인

        Args:
            symbol: 종목 코드
            current_price: 현재가

        Returns:
            트레일링 스탑 발동 여부
        """
        if not self.risk_management.trailing_stop:
            return False

        if symbol not in self.positions:
            return False

        position = self.positions[symbol]
        position.update_highest_price(current_price)

        if position.highest_price <= 0:
            return False

        drop_from_high = (position.highest_price - current_price) / position.highest_price

        return drop_from_high >= self.risk_management.trailing_stop_pct

    def record_equity(self, timestamp: datetime, current_prices: Dict[str, float]) -> None:
        """자산 기록"""
        equity = self.calculate_equity(current_prices)
        self.equity_history.append((timestamp, equity))

    def get_position_snapshot(self, current_prices: Dict[str, float]) -> List[Dict]:
        """현재 포지션 스냅샷"""
        snapshot = []
        for symbol, position in self.positions.items():
            current_price = current_prices.get(symbol, position.average_cost)
            snapshot.append({
                "symbol": symbol,
                "quantity": position.quantity,
                "average_cost": position.average_cost,
                "current_price": current_price,
                "market_value": position.current_value(current_price),
                "unrealized_pnl": position.unrealized_pnl(current_price),
                "unrealized_pnl_pct": position.unrealized_pnl_pct(current_price),
            })
        return snapshot


class BacktestEngine:
    """
    백테스트 엔진

    전략을 과거 데이터에 대해 시뮬레이션합니다.
    """

    def __init__(
        self,
        initial_capital: float = 10_000_000,  # 1천만원
        commission_rate: float = 0.00015,
        slippage_rate: float = 0.001,
    ):
        """
        Args:
            initial_capital: 초기 자본금
            commission_rate: 수수료율
            slippage_rate: 슬리피지율
        """
        self.initial_capital = initial_capital
        self.commission_rate = commission_rate
        self.slippage_rate = slippage_rate

    def run(
        self,
        strategy: StrategyDefinition,
        price_data: Dict[str, pd.DataFrame],
        signals: pd.DataFrame,
        position_size_pct: float = 0.1,
    ) -> BacktestResult:
        """
        백테스트 실행

        Args:
            strategy: 전략 정의
            price_data: 종목별 가격 데이터 {symbol: DataFrame}
                       DataFrame columns: date, open, high, low, close, volume
            signals: 신호 데이터 DataFrame
                    columns: date, symbol, signal_type, weight
            position_size_pct: 포지션 크기 비율 (기본 10%)

        Returns:
            BacktestResult
        """
        # 포트폴리오 초기화
        portfolio = Portfolio(
            initial_capital=self.initial_capital,
            commission_rate=self.commission_rate,
            slippage_rate=self.slippage_rate,
            risk_management=strategy.risk_management,
        )

        # 날짜 범위 확정
        all_dates = set()
        for df in price_data.values():
            if 'date' in df.columns:
                all_dates.update(pd.to_datetime(df['date']).dt.date)

        if not all_dates:
            raise InsufficientDataError("No price data available")

        sorted_dates = sorted(all_dates)
        start_date = datetime.combine(sorted_dates[0], datetime.min.time())
        end_date = datetime.combine(sorted_dates[-1], datetime.min.time())

        # 포지션 히스토리
        positions_history = []
        equity_records = []

        # 날짜별 시뮬레이션
        for date in sorted_dates:
            current_timestamp = datetime.combine(date, datetime.min.time())

            # 현재가 수집
            current_prices = {}
            for symbol, df in price_data.items():
                df_date = df[pd.to_datetime(df['date']).dt.date == date]
                if not df_date.empty:
                    current_prices[symbol] = float(df_date['close'].iloc[0])

            if not current_prices:
                continue

            current_equity = portfolio.calculate_equity(current_prices)

            # 1. 리스크 관리 체크 (손절/익절/트레일링)
            symbols_to_sell = []
            for symbol in list(portfolio.positions.keys()):
                if symbol not in current_prices:
                    continue

                price = current_prices[symbol]

                if portfolio.check_stop_loss(symbol, price):
                    logger.info(f"Stop loss triggered: {symbol} @ {price:.2f}")
                    symbols_to_sell.append((symbol, "stop_loss"))
                elif portfolio.check_take_profit(symbol, price):
                    logger.info(f"Take profit triggered: {symbol} @ {price:.2f}")
                    symbols_to_sell.append((symbol, "take_profit"))
                elif portfolio.check_trailing_stop(symbol, price):
                    logger.info(f"Trailing stop triggered: {symbol} @ {price:.2f}")
                    symbols_to_sell.append((symbol, "trailing_stop"))

            # 리스크 관리에 의한 매도 실행
            for symbol, reason in symbols_to_sell:
                if symbol in portfolio.positions:
                    position = portfolio.positions[symbol]
                    portfolio.execute_sell(
                        symbol=symbol,
                        quantity=position.quantity,
                        price=current_prices[symbol],
                        timestamp=current_timestamp,
                        strategy_id=strategy.strategy_id,
                        rule_name=reason,
                    )

            # 2. 신호 기반 거래
            date_signals = signals[pd.to_datetime(signals['date']).dt.date == date]

            for _, signal in date_signals.iterrows():
                symbol = signal['symbol']
                signal_type = signal['signal_type']
                weight = signal.get('weight', 1.0)

                if symbol not in current_prices:
                    continue

                price = current_prices[symbol]

                if signal_type == SignalType.BUY or signal_type == 'buy':
                    # 이미 보유 중이면 스킵
                    if symbol in portfolio.positions:
                        continue

                    # 포지션 크기 계산
                    position_value = current_equity * position_size_pct * weight
                    quantity = int(position_value / price)

                    if quantity > 0:
                        can_buy, reason = portfolio.can_buy(
                            symbol, quantity, price, current_equity
                        )
                        if can_buy:
                            portfolio.execute_buy(
                                symbol=symbol,
                                quantity=quantity,
                                price=price,
                                timestamp=current_timestamp,
                                strategy_id=strategy.strategy_id,
                                rule_name=signal.get('rule_name'),
                            )

                elif signal_type == SignalType.SELL or signal_type == 'sell':
                    if symbol in portfolio.positions:
                        position = portfolio.positions[symbol]
                        portfolio.execute_sell(
                            symbol=symbol,
                            quantity=position.quantity,
                            price=price,
                            timestamp=current_timestamp,
                            strategy_id=strategy.strategy_id,
                            rule_name=signal.get('rule_name'),
                        )

            # 자산 기록
            current_equity = portfolio.calculate_equity(current_prices)
            drawdown, drawdown_pct = portfolio.calculate_drawdown(current_equity)

            equity_records.append({
                'date': current_timestamp,
                'equity': current_equity,
                'cash': portfolio.cash,
                'drawdown': drawdown,
                'drawdown_pct': drawdown_pct,
            })

            # 포지션 스냅샷
            positions_history.append({
                'date': current_timestamp,
                'positions': portfolio.get_position_snapshot(current_prices),
            })

            # 최대 드로우다운 체크
            if drawdown_pct > self.initial_capital * strategy.risk_management.max_drawdown_pct:
                logger.warning(
                    f"Max drawdown exceeded: {drawdown_pct:.2f}% > "
                    f"{strategy.risk_management.max_drawdown_pct * 100:.2f}%"
                )

        # 최종 정산 (모든 포지션 청산)
        final_prices = {}
        for symbol, df in price_data.items():
            if not df.empty:
                final_prices[symbol] = float(df['close'].iloc[-1])

        for symbol in list(portfolio.positions.keys()):
            if symbol in final_prices:
                position = portfolio.positions[symbol]
                portfolio.execute_sell(
                    symbol=symbol,
                    quantity=position.quantity,
                    price=final_prices[symbol],
                    timestamp=end_date,
                    strategy_id=strategy.strategy_id,
                    rule_name="final_liquidation",
                )

        # Equity curve DataFrame 생성
        equity_curve = pd.DataFrame(equity_records)

        # 성과 지표 계산
        metrics = self._calculate_metrics(
            portfolio=portfolio,
            equity_curve=equity_curve,
            start_date=start_date,
            end_date=end_date,
        )

        return BacktestResult(
            strategy_id=strategy.strategy_id,
            start_date=start_date,
            end_date=end_date,
            initial_capital=self.initial_capital,
            final_capital=portfolio.cash,
            metrics=metrics,
            trades=portfolio.trades,
            equity_curve=equity_curve,
            positions_history=positions_history,
        )

    def _calculate_metrics(
        self,
        portfolio: Portfolio,
        equity_curve: pd.DataFrame,
        start_date: datetime,
        end_date: datetime,
    ) -> PerformanceMetrics:
        """
        성과 지표 계산

        Args:
            portfolio: 포트폴리오 객체
            equity_curve: 자산 곡선
            start_date: 시작일
            end_date: 종료일

        Returns:
            PerformanceMetrics
        """
        # 기본 수익률
        total_return = portfolio.cash - portfolio.initial_capital
        total_return_pct = (total_return / portfolio.initial_capital) * 100

        # 연간 수익률
        days = (end_date - start_date).days
        years = days / 365.0 if days > 0 else 1.0
        annualized_return = ((1 + total_return_pct / 100) ** (1 / years) - 1) * 100 if years > 0 else 0.0

        # 변동성 (일간 수익률 기준)
        volatility = 0.0
        sharpe_ratio = 0.0
        sortino_ratio = 0.0

        if len(equity_curve) > 1:
            equity_curve['daily_return'] = equity_curve['equity'].pct_change()
            daily_returns = equity_curve['daily_return'].dropna()

            if len(daily_returns) > 0:
                volatility = daily_returns.std() * np.sqrt(252) * 100  # 연환산

                # 샤프 비율 (무위험 수익률 3% 가정)
                risk_free_rate = 0.03
                excess_return = annualized_return / 100 - risk_free_rate
                if volatility > 0:
                    sharpe_ratio = excess_return / (volatility / 100)

                # 소르티노 비율 (하방 변동성만 사용)
                negative_returns = daily_returns[daily_returns < 0]
                if len(negative_returns) > 0:
                    downside_std = negative_returns.std() * np.sqrt(252)
                    if downside_std > 0:
                        sortino_ratio = excess_return / downside_std

        # 최대 드로우다운
        max_drawdown = 0.0
        max_drawdown_pct = 0.0
        if 'drawdown' in equity_curve.columns:
            max_drawdown = equity_curve['drawdown'].max()
            max_drawdown_pct = equity_curve['drawdown_pct'].max()

        # 거래 통계
        total_trades = len(portfolio.trades)

        # 매수-매도 쌍 분석
        buy_trades = [t for t in portfolio.trades if t.trade_type == TradeType.BUY]
        sell_trades = [t for t in portfolio.trades if t.trade_type == TradeType.SELL]

        wins = []
        losses = []
        holding_periods = []

        # 심플한 PnL 계산 (종목별 FIFO)
        positions_tracker: Dict[str, List[Trade]] = {}

        for trade in portfolio.trades:
            if trade.trade_type == TradeType.BUY:
                if trade.symbol not in positions_tracker:
                    positions_tracker[trade.symbol] = []
                positions_tracker[trade.symbol].append(trade)

            elif trade.trade_type == TradeType.SELL:
                if trade.symbol in positions_tracker and positions_tracker[trade.symbol]:
                    buy_trade = positions_tracker[trade.symbol].pop(0)

                    pnl = (trade.price - buy_trade.price) * trade.quantity
                    pnl -= (trade.commission + trade.slippage + buy_trade.commission + buy_trade.slippage)

                    if pnl > 0:
                        wins.append(pnl)
                    else:
                        losses.append(pnl)

                    holding_days = (trade.timestamp - buy_trade.timestamp).days
                    holding_periods.append(holding_days)

        winning_trades = len(wins)
        losing_trades = len(losses)
        win_rate = (winning_trades / (winning_trades + losing_trades) * 100) if (winning_trades + losing_trades) > 0 else 0.0

        average_win = sum(wins) / len(wins) if wins else 0.0
        average_loss = sum(losses) / len(losses) if losses else 0.0
        largest_win = max(wins) if wins else 0.0
        largest_loss = min(losses) if losses else 0.0

        # Profit Factor
        gross_profit = sum(wins) if wins else 0.0
        gross_loss = abs(sum(losses)) if losses else 0.0
        profit_factor = gross_profit / gross_loss if gross_loss > 0 else float('inf') if gross_profit > 0 else 0.0

        # 평균 보유 기간
        average_holding_period = sum(holding_periods) / len(holding_periods) if holding_periods else 0.0

        return PerformanceMetrics(
            total_return=total_return,
            total_return_pct=total_return_pct,
            annualized_return=annualized_return,
            volatility=volatility,
            sharpe_ratio=sharpe_ratio,
            sortino_ratio=sortino_ratio,
            max_drawdown=max_drawdown,
            max_drawdown_pct=max_drawdown_pct,
            win_rate=win_rate,
            profit_factor=profit_factor,
            total_trades=total_trades,
            winning_trades=winning_trades,
            losing_trades=losing_trades,
            average_win=average_win,
            average_loss=average_loss,
            largest_win=largest_win,
            largest_loss=largest_loss,
            average_holding_period=average_holding_period,
        )

    def run_multi_symbol(
        self,
        strategy: StrategyDefinition,
        price_data: Dict[str, pd.DataFrame],
        signal_generator,  # Callable that generates signals
        symbols: List[str],
        position_size_pct: float = 0.1,
    ) -> BacktestResult:
        """
        다중 종목 백테스트 실행

        신호 생성기를 사용하여 실시간으로 신호를 생성하면서 백테스트를 실행합니다.

        Args:
            strategy: 전략 정의
            price_data: 종목별 가격 데이터
            signal_generator: 신호 생성 함수 (price_data, date) -> List[signal]
            symbols: 대상 종목 목록
            position_size_pct: 포지션 크기 비율

        Returns:
            BacktestResult
        """
        # 모든 신호를 미리 생성
        all_signals = []

        all_dates = set()
        for symbol in symbols:
            if symbol in price_data:
                df = price_data[symbol]
                if 'date' in df.columns:
                    all_dates.update(pd.to_datetime(df['date']).dt.date)

        sorted_dates = sorted(all_dates)

        for date in sorted_dates:
            date_data = {}
            for symbol in symbols:
                if symbol in price_data:
                    df = price_data[symbol]
                    df_up_to_date = df[pd.to_datetime(df['date']).dt.date <= date]
                    if not df_up_to_date.empty:
                        date_data[symbol] = df_up_to_date

            if date_data:
                signals = signal_generator(date_data, date)
                for signal in signals:
                    signal['date'] = datetime.combine(date, datetime.min.time())
                    all_signals.append(signal)

        signals_df = pd.DataFrame(all_signals) if all_signals else pd.DataFrame(
            columns=['date', 'symbol', 'signal_type', 'weight']
        )

        return self.run(
            strategy=strategy,
            price_data=price_data,
            signals=signals_df,
            position_size_pct=position_size_pct,
        )
