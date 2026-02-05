"""
Backtest Engine

백테스트 실행 엔진.
과거 데이터를 사용하여 전략의 성과를 시뮬레이션합니다.
"""

import logging
import time
import uuid
import math
from datetime import datetime
from typing import Dict, List, Optional, Tuple
import numpy as np
import pandas as pd

from domain.strategy.models import StrategyDefinition, SignalType
from domain.strategy.interpreter import StrategyInterpreter
from domain.backtest.models import (
    Trade,
    TradeType,
    Position,
    BacktestConfig,
    BacktestResult,
    PerformanceMetrics,
    EquityPoint,
)
from domain.common.exceptions import (
    BacktestError,
    BacktestConfigError,
    InsufficientDataError,
    InsufficientCapitalError,
)

logger = logging.getLogger(__name__)


class BacktestEngine:
    """
    백테스트 실행 엔진

    과거 시장 데이터를 사용하여 전략을 시뮬레이션하고
    성과 지표를 계산합니다.

    사용 예시:
        engine = BacktestEngine()
        result = engine.run(
            strategy=my_strategy,
            price_data=historical_data,
            symbol="005930",
            config=BacktestConfig(initial_capital=10_000_000)
        )
    """

    def __init__(self):
        self.interpreter = StrategyInterpreter()

    def run(
        self,
        strategy: StrategyDefinition,
        price_data: pd.DataFrame,
        symbol: str,
        config: Optional[BacktestConfig] = None,
        start_date: Optional[datetime] = None,
        end_date: Optional[datetime] = None,
    ) -> BacktestResult:
        """
        백테스트 실행

        Args:
            strategy: 실행할 전략 정의
            price_data: OHLCV 시세 데이터 (index: DatetimeIndex)
            symbol: 종목 코드
            config: 백테스트 설정 (None이면 기본값 사용)
            start_date: 시작일 (None이면 데이터 시작일)
            end_date: 종료일 (None이면 데이터 종료일)

        Returns:
            BacktestResult: 백테스트 결과

        Raises:
            BacktestError: 백테스트 실행 중 오류 발생
            InsufficientDataError: 데이터 부족
        """
        start_time = time.perf_counter()
        backtest_id = str(uuid.uuid4())[:8]

        # 설정 초기화
        if config is None:
            config = BacktestConfig()

        try:
            config.validate()
        except ValueError as e:
            raise BacktestConfigError(str(e))

        # 데이터 검증
        if price_data.empty:
            raise InsufficientDataError(
                f"No price data for {symbol}",
                required=50,
                actual=0
            )

        if len(price_data) < 50:
            raise InsufficientDataError(
                f"Insufficient data for {symbol}: {len(price_data)} rows (min 50)",
                required=50,
                actual=len(price_data)
            )

        # 날짜 필터링
        data = self._filter_date_range(price_data, start_date, end_date)

        if len(data) < 50:
            raise InsufficientDataError(
                f"Insufficient data after date filter: {len(data)} rows",
                required=50,
                actual=len(data)
            )

        try:
            # 백테스트 실행
            trades, equity_curve, final_capital = self._execute_backtest(
                strategy=strategy,
                data=data,
                symbol=symbol,
                config=config
            )

            # 성과 지표 계산
            metrics = self._calculate_metrics(
                trades=trades,
                equity_curve=equity_curve,
                initial_capital=config.initial_capital,
                data=data
            )

            execution_time = (time.perf_counter() - start_time) * 1000

            return BacktestResult(
                backtest_id=backtest_id,
                strategy_id=strategy.strategy_id,
                strategy_name=strategy.name,
                symbol=symbol,
                config=config,
                initial_capital=config.initial_capital,
                final_capital=final_capital,
                total_pnl=final_capital - config.initial_capital,
                total_return_pct=((final_capital / config.initial_capital) - 1) * 100,
                trades=trades,
                equity_curve=equity_curve,
                metrics=metrics,
                execution_time_ms=execution_time
            )

        except Exception as e:
            logger.exception(f"Backtest failed for {strategy.strategy_id} on {symbol}")
            raise BacktestError(str(e), strategy.strategy_id, symbol)

    def _filter_date_range(
        self,
        data: pd.DataFrame,
        start_date: Optional[datetime],
        end_date: Optional[datetime]
    ) -> pd.DataFrame:
        """날짜 범위 필터링"""
        if start_date:
            data = data[data.index >= pd.Timestamp(start_date)]
        if end_date:
            data = data[data.index <= pd.Timestamp(end_date)]
        return data

    def _execute_backtest(
        self,
        strategy: StrategyDefinition,
        data: pd.DataFrame,
        symbol: str,
        config: BacktestConfig
    ) -> Tuple[List[Trade], List[EquityPoint], float]:
        """
        백테스트 메인 루프 실행

        Returns:
            Tuple[trades, equity_curve, final_capital]
        """
        trades: List[Trade] = []
        equity_curve: List[EquityPoint] = []

        cash = config.initial_capital
        position = Position(symbol=symbol)
        peak_equity = config.initial_capital
        trade_counter = 0

        # 워밍업 기간 (지표 계산을 위해 최소 50일 필요)
        warmup_period = 50

        for i in range(warmup_period, len(data)):
            current_date = data.index[i]
            current_price = data['close'].iloc[i]

            # 워밍업 기간까지의 데이터로 전략 실행
            historical_data = data.iloc[:i+1]

            # 포지션 미실현 손익 업데이트
            position.update_unrealized_pnl(current_price)

            # 현재 자산 계산
            positions_value = position.quantity * current_price if position.is_open else 0
            current_equity = cash + positions_value

            # 손절/익절 체크 (포지션이 있는 경우)
            if position.is_open:
                pnl_pct = (current_price - position.avg_price) / position.avg_price

                # 손절
                if pnl_pct <= -config.stop_loss_pct:
                    trade, cash_change = self._close_position(
                        position=position,
                        current_price=current_price,
                        current_date=current_date,
                        config=config,
                        trade_counter=trade_counter,
                        rule_name="stop_loss"
                    )
                    trades.append(trade)
                    cash += cash_change
                    trade_counter += 1
                    position = Position(symbol=symbol)

                # 익절
                elif pnl_pct >= config.take_profit_pct:
                    trade, cash_change = self._close_position(
                        position=position,
                        current_price=current_price,
                        current_date=current_date,
                        config=config,
                        trade_counter=trade_counter,
                        rule_name="take_profit"
                    )
                    trades.append(trade)
                    cash += cash_change
                    trade_counter += 1
                    position = Position(symbol=symbol)

            # 전략 신호 평가
            try:
                result = self.interpreter.execute(
                    strategy=strategy,
                    market_data=historical_data,
                    target_date=str(current_date.date()) if hasattr(current_date, 'date') else str(current_date)
                )
                signals = result.get("signals", [])
            except Exception as e:
                logger.warning(f"Strategy execution error at {current_date}: {e}")
                signals = []

            # 신호에 따른 매매 실행
            for signal in signals:
                signal_type = signal.get("type", "").lower()
                rule_name = signal.get("rule", "unknown")

                if signal_type == "buy" and not position.is_open:
                    # 매수: 최대 포지션 크기만큼
                    max_investment = current_equity * config.max_position_size
                    available_cash = min(cash, max_investment)

                    if available_cash > current_price:  # 최소 1주 이상 매수 가능
                        trade, position, cash_change = self._open_position(
                            symbol=symbol,
                            current_price=current_price,
                            current_date=current_date,
                            available_cash=available_cash,
                            config=config,
                            trade_counter=trade_counter,
                            rule_name=rule_name
                        )
                        trades.append(trade)
                        cash += cash_change
                        trade_counter += 1

                elif signal_type == "sell" and position.is_open:
                    # 매도
                    trade, cash_change = self._close_position(
                        position=position,
                        current_price=current_price,
                        current_date=current_date,
                        config=config,
                        trade_counter=trade_counter,
                        rule_name=rule_name
                    )
                    trades.append(trade)
                    cash += cash_change
                    trade_counter += 1
                    position = Position(symbol=symbol)

            # 자산 곡선 기록
            positions_value = position.quantity * current_price if position.is_open else 0
            current_equity = cash + positions_value

            # Drawdown 계산
            if current_equity > peak_equity:
                peak_equity = current_equity

            drawdown = peak_equity - current_equity
            drawdown_pct = (drawdown / peak_equity) * 100 if peak_equity > 0 else 0

            equity_curve.append(EquityPoint(
                timestamp=current_date.to_pydatetime() if hasattr(current_date, 'to_pydatetime') else current_date,
                equity=current_equity,
                cash=cash,
                positions_value=positions_value,
                drawdown=drawdown,
                drawdown_pct=drawdown_pct
            ))

        # 마지막 포지션 청산 (백테스트 종료 시)
        if position.is_open:
            final_price = data['close'].iloc[-1]
            final_date = data.index[-1]
            trade, cash_change = self._close_position(
                position=position,
                current_price=final_price,
                current_date=final_date,
                config=config,
                trade_counter=trade_counter,
                rule_name="backtest_end"
            )
            trades.append(trade)
            cash += cash_change

        return trades, equity_curve, cash

    def _open_position(
        self,
        symbol: str,
        current_price: float,
        current_date: pd.Timestamp,
        available_cash: float,
        config: BacktestConfig,
        trade_counter: int,
        rule_name: str
    ) -> Tuple[Trade, Position, float]:
        """
        매수 포지션 진입

        Returns:
            Tuple[trade, new_position, cash_change]
        """
        # 수수료/슬리피지 고려한 매수 가능 수량
        effective_price = current_price * (1 + config.slippage_rate)
        commission = available_cash * config.commission_rate
        net_cash = available_cash - commission
        quantity = int(net_cash / effective_price)

        if quantity <= 0:
            raise InsufficientCapitalError(
                f"Cannot buy at least 1 share",
                required=effective_price,
                available=net_cash
            )

        total_cost = quantity * effective_price
        actual_commission = total_cost * config.commission_rate
        slippage = quantity * current_price * config.slippage_rate

        trade = Trade(
            trade_id=f"T{trade_counter:04d}",
            symbol=symbol,
            trade_type=TradeType.BUY,
            quantity=quantity,
            price=effective_price,
            timestamp=current_date.to_pydatetime() if hasattr(current_date, 'to_pydatetime') else current_date,
            rule_name=rule_name,
            commission=actual_commission,
            slippage=slippage
        )

        position = Position(
            symbol=symbol,
            quantity=quantity,
            avg_price=effective_price,
            entry_timestamp=current_date.to_pydatetime() if hasattr(current_date, 'to_pydatetime') else current_date
        )

        cash_change = -(total_cost + actual_commission)

        return trade, position, cash_change

    def _close_position(
        self,
        position: Position,
        current_price: float,
        current_date: pd.Timestamp,
        config: BacktestConfig,
        trade_counter: int,
        rule_name: str
    ) -> Tuple[Trade, float]:
        """
        매도 포지션 청산

        Returns:
            Tuple[trade, cash_change]
        """
        effective_price = current_price * (1 - config.slippage_rate)
        total_value = position.quantity * effective_price
        commission = total_value * config.commission_rate
        slippage = position.quantity * current_price * config.slippage_rate

        trade = Trade(
            trade_id=f"T{trade_counter:04d}",
            symbol=position.symbol,
            trade_type=TradeType.SELL,
            quantity=position.quantity,
            price=effective_price,
            timestamp=current_date.to_pydatetime() if hasattr(current_date, 'to_pydatetime') else current_date,
            rule_name=rule_name,
            commission=commission,
            slippage=slippage
        )

        cash_change = total_value - commission

        # 실현 손익 계산
        realized_pnl = (effective_price - position.avg_price) * position.quantity - commission
        position.realized_pnl = realized_pnl

        return trade, cash_change

    def _calculate_metrics(
        self,
        trades: List[Trade],
        equity_curve: List[EquityPoint],
        initial_capital: float,
        data: pd.DataFrame
    ) -> PerformanceMetrics:
        """성과 지표 계산"""
        metrics = PerformanceMetrics()

        if not equity_curve:
            return metrics

        # 기간 정보
        metrics.start_date = equity_curve[0].timestamp
        metrics.end_date = equity_curve[-1].timestamp
        metrics.trading_days = len(equity_curve)

        # 수익률 계산
        final_equity = equity_curve[-1].equity
        metrics.total_return = ((final_equity / initial_capital) - 1) * 100

        # 연환산 수익률
        years = metrics.trading_days / 252  # 연간 거래일 252일 가정
        if years > 0:
            metrics.annualized_return = ((final_equity / initial_capital) ** (1 / years) - 1) * 100

        # 최대 낙폭
        max_dd = max(ep.drawdown_pct for ep in equity_curve) if equity_curve else 0
        metrics.max_drawdown = max_dd

        # 최대 낙폭 지속 기간
        metrics.max_drawdown_duration_days = self._calculate_max_drawdown_duration(equity_curve)

        # 일간 수익률 계산
        daily_returns = self._calculate_daily_returns(equity_curve)

        if len(daily_returns) > 1:
            # 변동성 (연환산)
            metrics.volatility = float(np.std(daily_returns) * np.sqrt(252) * 100)

            # 샤프 비율 (무위험 수익률 0% 가정)
            avg_return = np.mean(daily_returns)
            std_return = np.std(daily_returns)
            if std_return > 0:
                metrics.sharpe_ratio = float((avg_return * 252) / (std_return * np.sqrt(252)))

            # 소르티노 비율 (하방 변동성만 고려)
            negative_returns = [r for r in daily_returns if r < 0]
            if negative_returns:
                downside_std = np.std(negative_returns)
                if downside_std > 0:
                    metrics.sortino_ratio = float((avg_return * 252) / (downside_std * np.sqrt(252)))

        # 칼마 비율
        if metrics.max_drawdown > 0:
            metrics.calmar_ratio = metrics.annualized_return / metrics.max_drawdown

        # 거래 통계
        self._calculate_trade_statistics(trades, metrics)

        return metrics

    def _calculate_daily_returns(self, equity_curve: List[EquityPoint]) -> List[float]:
        """일간 수익률 계산"""
        if len(equity_curve) < 2:
            return []

        returns = []
        for i in range(1, len(equity_curve)):
            prev_equity = equity_curve[i-1].equity
            curr_equity = equity_curve[i].equity
            if prev_equity > 0:
                returns.append((curr_equity - prev_equity) / prev_equity)

        return returns

    def _calculate_max_drawdown_duration(self, equity_curve: List[EquityPoint]) -> int:
        """최대 낙폭 지속 기간 계산"""
        if not equity_curve:
            return 0

        max_duration = 0
        current_duration = 0
        peak = equity_curve[0].equity

        for ep in equity_curve:
            if ep.equity >= peak:
                peak = ep.equity
                current_duration = 0
            else:
                current_duration += 1
                max_duration = max(max_duration, current_duration)

        return max_duration

    def _calculate_trade_statistics(self, trades: List[Trade], metrics: PerformanceMetrics) -> None:
        """거래 통계 계산"""
        if not trades:
            return

        # 매수/매도 쌍으로 거래 분석
        buy_trades = [t for t in trades if t.trade_type == TradeType.BUY]
        sell_trades = [t for t in trades if t.trade_type == TradeType.SELL]

        # 완료된 라운드트립 거래 수
        completed_trades = min(len(buy_trades), len(sell_trades))
        metrics.total_trades = completed_trades

        if completed_trades == 0:
            return

        # 각 거래의 손익 계산
        trade_pnls = []
        for i in range(completed_trades):
            buy = buy_trades[i]
            sell = sell_trades[i]

            # 손익 = 매도금액 - 매수금액 - 수수료
            pnl = (sell.price * sell.quantity) - (buy.price * buy.quantity) - buy.commission - sell.commission
            trade_pnls.append(pnl)

        # 승/패 거래
        winning_pnls = [p for p in trade_pnls if p > 0]
        losing_pnls = [p for p in trade_pnls if p <= 0]

        metrics.winning_trades = len(winning_pnls)
        metrics.losing_trades = len(losing_pnls)

        # 승률
        metrics.win_rate = (metrics.winning_trades / metrics.total_trades) * 100 if metrics.total_trades > 0 else 0

        # 평균 이익/손실
        metrics.avg_win = sum(winning_pnls) / len(winning_pnls) if winning_pnls else 0
        metrics.avg_loss = sum(losing_pnls) / len(losing_pnls) if losing_pnls else 0

        # 손익비
        total_wins = sum(winning_pnls)
        total_losses = abs(sum(losing_pnls))
        metrics.profit_factor = total_wins / total_losses if total_losses > 0 else float('inf') if total_wins > 0 else 0

        # 평균 거래 수익률
        metrics.avg_trade_return = sum(trade_pnls) / len(trade_pnls) if trade_pnls else 0
