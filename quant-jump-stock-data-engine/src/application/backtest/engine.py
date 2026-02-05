"""
Backtest Engine

전략 백테스트 실행 엔진.
과거 데이터에서 전략을 시뮬레이션하고 성과를 측정합니다.

사용 예시:
    from application.backtest import BacktestEngine, BacktestConfig, YFinanceDataLoader
    from domain.strategy.models import StrategyDefinition

    config = BacktestConfig(
        start_date=date(2023, 1, 1),
        end_date=date(2024, 1, 1),
        initial_capital=Decimal("10000000"),
        symbols=["005930", "000660"]
    )

    engine = BacktestEngine(
        data_loader=YFinanceDataLoader(),
        config=config
    )

    result = engine.run(strategy)
"""

import logging
import math
import time
from dataclasses import dataclass, field
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP
from typing import Dict, List, Optional, Set, Tuple

import numpy as np
import pandas as pd

from domain.strategy.models import StrategyDefinition, SignalType
from domain.strategy.interpreter import StrategyInterpreter
from domain.backtest.models import Portfolio, Position, ExitReason, TradeType
from domain.backtest.risk_manager import RiskManager

from .data_loader import DataLoader
from .result import BacktestResult, BacktestTrade, EquityCurvePoint

logger = logging.getLogger(__name__)


@dataclass
class BacktestConfig:
    """
    백테스트 설정

    Attributes:
        start_date: 백테스트 시작일
        end_date: 백테스트 종료일
        initial_capital: 초기 자본금
        symbols: 거래 대상 종목 목록
        commission_rate: 수수료율 (0.00015 = 0.015%)
        slippage_rate: 슬리피지율 (0.001 = 0.1%)
        max_positions: 최대 동시 보유 종목 수
        position_size_pct: 포지션당 비중 (0.1 = 10%)
        rebalance_frequency: 리밸런싱 주기 (daily, weekly, monthly)
    """
    start_date: date
    end_date: date
    initial_capital: Decimal = Decimal("10000000")
    symbols: List[str] = field(default_factory=list)
    commission_rate: Decimal = Decimal("0.00015")  # 0.015%
    slippage_rate: Decimal = Decimal("0.001")      # 0.1%
    max_positions: int = 10
    position_size_pct: Decimal = Decimal("0.1")    # 10%
    rebalance_frequency: str = "daily"


class BacktestEngine:
    """
    백테스트 실행 엔진

    전략 DSL을 받아 과거 데이터에서 시뮬레이션을 수행합니다.

    주요 기능:
    - 전략 시그널에 따른 매수/매도
    - Stop Loss / Take Profit 자동 청산
    - 수익 곡선 및 성과 지표 계산
    """

    def __init__(
        self,
        data_loader: DataLoader,
        config: BacktestConfig
    ):
        """
        Args:
            data_loader: 데이터 로더 인스턴스
            config: 백테스트 설정
        """
        self.data_loader = data_loader
        self.config = config
        self.interpreter = StrategyInterpreter()

        # 실행 중 상태
        self._data: Dict[str, pd.DataFrame] = {}
        self._portfolio: Optional[Portfolio] = None
        self._risk_manager: Optional[RiskManager] = None
        self._equity_curve: List[EquityCurvePoint] = []
        self._high_watermark: Decimal = Decimal("0")

    def run(self, strategy: StrategyDefinition) -> BacktestResult:
        """
        백테스트 실행

        Args:
            strategy: 전략 정의 (DSL)

        Returns:
            BacktestResult: 백테스트 결과
        """
        start_time = time.time()

        try:
            # 1. 초기화
            self._initialize(strategy)

            # 2. 데이터 로드
            self._load_data()

            if not self._data:
                return self._create_error_result(
                    strategy, "No data loaded for any symbol"
                )

            # 3. 거래일 목록 생성
            trading_dates = self._get_trading_dates()

            if not trading_dates:
                return self._create_error_result(
                    strategy, "No trading dates in the specified period"
                )

            logger.info(
                f"Starting backtest: {strategy.name}, "
                f"{len(trading_dates)} trading days, "
                f"{len(self.config.symbols)} symbols"
            )

            # 4. 일별 시뮬레이션
            for current_date in trading_dates:
                self._process_day(strategy, current_date)

            # 5. 결과 생성
            execution_time = time.time() - start_time
            result = self._create_result(strategy, execution_time)

            logger.info(
                f"Backtest completed: {strategy.name}, "
                f"Total return: {result.total_return:.2f}%, "
                f"Trades: {result.total_trades}"
            )

            return result

        except Exception as e:
            logger.exception(f"Backtest failed: {strategy.name}")
            execution_time = time.time() - start_time
            return self._create_error_result(
                strategy, str(e), execution_time
            )

    def _initialize(self, strategy: StrategyDefinition) -> None:
        """초기화"""
        # 포트폴리오 초기화
        self._portfolio = Portfolio(
            initial_capital=self.config.initial_capital
        )

        # 리스크 매니저 초기화 (전략의 리스크 설정 사용)
        self._risk_manager = RiskManager.from_risk_management(
            strategy.risk_management
        )

        # 상태 초기화
        self._equity_curve = []
        self._high_watermark = self.config.initial_capital
        self._data = {}

    def _load_data(self) -> None:
        """데이터 로드"""
        symbols = self.config.symbols

        if not symbols:
            logger.warning("No symbols specified")
            return

        self._data = self.data_loader.load(
            symbols=symbols,
            start_date=self.config.start_date,
            end_date=self.config.end_date
        )

        logger.info(f"Loaded data for {len(self._data)} symbols")

    def _get_trading_dates(self) -> List[date]:
        """거래일 목록 생성"""
        if not self._data:
            return []

        # 모든 심볼의 인덱스 합집합
        all_dates: Set[date] = set()

        for df in self._data.values():
            if not df.empty:
                dates = {d.date() if hasattr(d, 'date') else d for d in df.index}
                all_dates.update(dates)

        # 범위 내 날짜만 필터링 후 정렬
        filtered_dates = [
            d for d in all_dates
            if self.config.start_date <= d <= self.config.end_date
        ]

        return sorted(filtered_dates)

    def _process_day(
        self,
        strategy: StrategyDefinition,
        current_date: date
    ) -> None:
        """단일 거래일 처리"""
        # 1. 현재 가격 업데이트
        current_prices = self._get_prices_at_date(current_date)

        if not current_prices:
            return

        # 2. 포트폴리오 가격 업데이트
        self._portfolio.update_prices(current_prices)
        self._portfolio.current_date = current_date

        # 3. 리스크 체크 (Stop Loss / Take Profit)
        risk_exits = self._risk_manager.check_risk_exits(
            self._portfolio, current_prices, current_date
        )

        # 4. 리스크 기반 청산 실행
        for exit_result in risk_exits:
            price = current_prices.get(exit_result.symbol)
            if price:
                self._execute_sell(
                    exit_result.symbol,
                    current_date,
                    price,
                    exit_result.reason
                )

        # 5. 전략 시그널 처리
        self._process_signals(strategy, current_date, current_prices)

        # 6. 수익 곡선 업데이트
        self._update_equity_curve(current_date)

    def _get_prices_at_date(self, target_date: date) -> Dict[str, Decimal]:
        """특정 날짜의 종가 조회"""
        prices = {}

        for symbol, df in self._data.items():
            if df.empty:
                continue

            # 날짜로 인덱싱
            target_dt = pd.Timestamp(target_date)

            if target_dt in df.index:
                close_price = df.loc[target_dt, "close"]
                if pd.notna(close_price):
                    prices[symbol] = Decimal(str(close_price))

        return prices

    def _process_signals(
        self,
        strategy: StrategyDefinition,
        current_date: date,
        prices: Dict[str, Decimal]
    ) -> None:
        """전략 시그널 처리"""
        for symbol in self.config.symbols:
            if symbol not in self._data:
                continue

            df = self._data[symbol]

            # 해당 날짜까지의 데이터만 사용 (미래 데이터 방지)
            target_dt = pd.Timestamp(current_date)
            historical_data = df[df.index <= target_dt]

            if historical_data.empty or len(historical_data) < 50:
                continue

            # 전략 실행
            try:
                result = self.interpreter.execute(
                    strategy,
                    historical_data,
                    str(current_date)
                )

                signals = result.get("signals", [])

                for signal in signals:
                    signal_type = signal.get("type", "").lower()
                    price = prices.get(symbol)

                    if not price:
                        continue

                    if signal_type == "buy":
                        self._execute_buy(symbol, current_date, price)
                    elif signal_type == "sell":
                        self._execute_sell(
                            symbol, current_date, price,
                            ExitReason.STRATEGY_SIGNAL
                        )

            except Exception as e:
                logger.debug(f"Signal processing error for {symbol}: {e}")

    def _execute_buy(
        self,
        symbol: str,
        trade_date: date,
        price: Decimal
    ) -> None:
        """매수 실행"""
        # 이미 보유 중이면 스킵
        if symbol in self._portfolio.positions:
            return

        # 최대 포지션 수 체크
        if self._portfolio.position_count >= self.config.max_positions:
            return

        # 포지션 크기 계산
        position_value = self._portfolio.total_value * self.config.position_size_pct
        quantity = int(position_value / price)

        if quantity <= 0:
            return

        # 수수료 계산
        amount = price * quantity
        commission = amount * self.config.commission_rate

        # 슬리피지 적용 (매수는 더 비싸게)
        adjusted_price = price * (1 + self.config.slippage_rate)

        # 매수 실행
        trade = self._portfolio.open_position(
            symbol=symbol,
            trade_date=trade_date,
            price=adjusted_price,
            quantity=quantity,
            commission=commission
        )

        if trade:
            logger.debug(
                f"[BUY] {symbol}: {quantity}주 @ {adjusted_price:,.0f}"
            )

    def _execute_sell(
        self,
        symbol: str,
        trade_date: date,
        price: Decimal,
        exit_reason: ExitReason
    ) -> None:
        """매도 실행"""
        if symbol not in self._portfolio.positions:
            return

        # 수수료 계산
        position = self._portfolio.positions[symbol]
        amount = price * position.quantity
        commission = amount * self.config.commission_rate

        # 슬리피지 적용 (매도는 더 싸게)
        adjusted_price = price * (1 - self.config.slippage_rate)

        # 매도 실행
        trade = self._portfolio.close_position(
            symbol=symbol,
            trade_date=trade_date,
            price=adjusted_price,
            exit_reason=exit_reason,
            commission=commission
        )

        if trade:
            logger.debug(
                f"[SELL:{exit_reason.value}] {symbol}: "
                f"{trade.quantity}주 @ {adjusted_price:,.0f}, "
                f"PnL: {trade.realized_pnl:+,.0f}"
            )

    def _update_equity_curve(self, current_date: date) -> None:
        """수익 곡선 업데이트"""
        total_value = self._portfolio.total_value

        # 고점 업데이트
        if total_value > self._high_watermark:
            self._high_watermark = total_value

        # 낙폭 계산
        drawdown_pct = Decimal("0")
        if self._high_watermark > 0:
            drawdown_pct = (
                (total_value / self._high_watermark) - 1
            ) * 100

        # 포지션 가치 계산
        positions_value = sum(
            p.market_value for p in self._portfolio.positions.values()
        )

        point = EquityCurvePoint(
            date=current_date,
            equity=total_value,
            cash=self._portfolio.cash,
            positions_value=positions_value,
            drawdown_pct=drawdown_pct
        )

        self._equity_curve.append(point)

    def _create_result(
        self,
        strategy: StrategyDefinition,
        execution_time: float
    ) -> BacktestResult:
        """결과 생성"""
        # 거래 기록 변환
        trades = [
            BacktestTrade(
                symbol=t.symbol,
                trade_type=t.trade_type.value,
                trade_date=t.trade_date,
                price=t.price,
                quantity=t.quantity,
                amount=t.amount,
                commission=t.commission,
                exit_reason=t.exit_reason.value if t.exit_reason else None,
                realized_pnl=t.realized_pnl,
                realized_pnl_pct=t.realized_pnl_pct,
                entry_price=t.entry_price
            )
            for t in self._portfolio.trades
        ]

        # 청산 사유별 집계
        exit_reason_counts = {}
        sell_trades = [t for t in trades if t.trade_type == "sell"]
        for trade in sell_trades:
            reason = trade.exit_reason or "unknown"
            exit_reason_counts[reason] = exit_reason_counts.get(reason, 0) + 1

        # 성과 지표 계산
        metrics = self._calculate_metrics(trades)

        # MDD 계산
        mdd = Decimal("0")
        for point in self._equity_curve:
            if point.drawdown_pct < mdd:
                mdd = point.drawdown_pct

        result = BacktestResult(
            strategy_id=0,  # 나중에 DB 저장 시 설정
            strategy_name=strategy.name,
            start_date=self.config.start_date,
            end_date=self.config.end_date,
            initial_capital=self.config.initial_capital,
            final_value=self._portfolio.total_value,
            total_return=metrics["total_return_pct"],
            cagr=metrics["cagr"],
            mdd=mdd,
            sharpe_ratio=metrics["sharpe_ratio"],
            sortino_ratio=metrics["sortino_ratio"],
            volatility=metrics["volatility"],
            win_rate=metrics["win_rate"],
            total_trades=metrics["total_trades"],
            winning_trades=metrics["winning_trades"],
            losing_trades=metrics["losing_trades"],
            avg_win=metrics["avg_win"],
            avg_loss=metrics["avg_loss"],
            largest_win=metrics["largest_win"],
            largest_loss=metrics["largest_loss"],
            profit_factor=metrics["profit_factor"],
            avg_holding_days=metrics["avg_holding_days"],
            trades=trades,
            equity_curve=self._equity_curve,
            exit_reason_counts=exit_reason_counts,
            execution_time_seconds=execution_time,
            metadata={
                "symbols": self.config.symbols,
                "commission_rate": float(self.config.commission_rate),
                "slippage_rate": float(self.config.slippage_rate),
            }
        )

        return result

    def _calculate_metrics(
        self,
        trades: List[BacktestTrade]
    ) -> Dict[str, Optional[Decimal]]:
        """
        성과 지표 계산

        portfolio.py의 로직을 Decimal 기반으로 통합

        Returns:
            성과 지표 딕셔너리
        """
        # 기본 수익률
        total_return = self._portfolio.total_pnl
        total_return_pct = self._portfolio.total_pnl_pct

        # 연간 수익률 (CAGR)
        days = (self.config.end_date - self.config.start_date).days
        years = Decimal(str(days)) / Decimal("365") if days > 0 else Decimal("1")

        cagr = Decimal("0")
        if years > 0 and total_return_pct > Decimal("-100"):
            # CAGR = (final/initial)^(1/years) - 1
            ratio = (Decimal("100") + total_return_pct) / Decimal("100")
            if ratio > 0:
                cagr = (Decimal(str(float(ratio) ** (1 / float(years)))) - 1) * 100

        # 변동성 및 샤프/소르티노 비율 계산
        volatility = None
        sharpe_ratio = None
        sortino_ratio = None

        if len(self._equity_curve) > 1:
            # 일간 수익률 계산
            equities = [float(p.equity) for p in self._equity_curve]
            daily_returns = []
            for i in range(1, len(equities)):
                if equities[i-1] > 0:
                    ret = (equities[i] - equities[i-1]) / equities[i-1]
                    daily_returns.append(ret)

            if len(daily_returns) > 0:
                returns_array = np.array(daily_returns)

                # 연환산 변동성 (일간 표준편차 * sqrt(252))
                daily_std = float(np.std(returns_array))
                annual_vol = daily_std * math.sqrt(252)
                volatility = Decimal(str(annual_vol * 100)).quantize(
                    Decimal("0.01"), rounding=ROUND_HALF_UP
                )

                # 샤프 비율 (무위험 수익률 3% 가정)
                risk_free_rate = 0.03
                excess_return = float(cagr) / 100 - risk_free_rate
                if annual_vol > 0:
                    sharpe = excess_return / annual_vol
                    sharpe_ratio = Decimal(str(sharpe)).quantize(
                        Decimal("0.01"), rounding=ROUND_HALF_UP
                    )

                # 소르티노 비율 (하방 변동성만 사용)
                negative_returns = returns_array[returns_array < 0]
                if len(negative_returns) > 0:
                    downside_std = float(np.std(negative_returns)) * math.sqrt(252)
                    if downside_std > 0:
                        sortino = excess_return / downside_std
                        sortino_ratio = Decimal(str(sortino)).quantize(
                            Decimal("0.01"), rounding=ROUND_HALF_UP
                        )

        # 거래 통계
        sell_trades = [t for t in trades if t.trade_type == "sell"]
        total_trades = len(sell_trades)

        # 승/패 분석 (FIFO 기반)
        wins: List[Decimal] = []
        losses: List[Decimal] = []
        holding_days: List[int] = []

        buy_trades_map: Dict[str, List[BacktestTrade]] = {}

        for trade in trades:
            if trade.trade_type == "buy":
                if trade.symbol not in buy_trades_map:
                    buy_trades_map[trade.symbol] = []
                buy_trades_map[trade.symbol].append(trade)
            elif trade.trade_type == "sell":
                if trade.symbol in buy_trades_map and buy_trades_map[trade.symbol]:
                    buy_trade = buy_trades_map[trade.symbol].pop(0)

                    # PnL 계산
                    pnl = (trade.price - buy_trade.price) * trade.quantity
                    pnl -= (trade.commission + buy_trade.commission)

                    if pnl > 0:
                        wins.append(pnl)
                    else:
                        losses.append(pnl)

                    # 보유 기간
                    days_held = (trade.trade_date - buy_trade.trade_date).days
                    holding_days.append(days_held)

        winning_trades = len(wins)
        losing_trades = len(losses)

        # 승률
        win_rate = None
        if total_trades > 0:
            win_rate = Decimal(str(winning_trades / total_trades * 100)).quantize(
                Decimal("0.01"), rounding=ROUND_HALF_UP
            )

        # 평균 수익/손실
        avg_win = None
        avg_loss = None
        largest_win = None
        largest_loss = None

        if wins:
            avg_win = sum(wins) / len(wins)
            largest_win = max(wins)

        if losses:
            avg_loss = sum(losses) / len(losses)
            largest_loss = min(losses)

        # Profit Factor
        profit_factor = None
        gross_profit = sum(wins) if wins else Decimal("0")
        gross_loss = abs(sum(losses)) if losses else Decimal("0")

        if gross_loss > 0:
            profit_factor = gross_profit / gross_loss
        elif gross_profit > 0:
            profit_factor = Decimal("999.99")  # 무한대 대신

        # 평균 보유 기간
        avg_holding_days = None
        if holding_days:
            avg_holding_days = Decimal(str(sum(holding_days) / len(holding_days))).quantize(
                Decimal("0.1"), rounding=ROUND_HALF_UP
            )

        return {
            "total_return": total_return,
            "total_return_pct": total_return_pct,
            "cagr": cagr,
            "volatility": volatility,
            "sharpe_ratio": sharpe_ratio,
            "sortino_ratio": sortino_ratio,
            "total_trades": total_trades,
            "winning_trades": winning_trades,
            "losing_trades": losing_trades,
            "win_rate": win_rate,
            "avg_win": avg_win,
            "avg_loss": avg_loss,
            "largest_win": largest_win,
            "largest_loss": largest_loss,
            "profit_factor": profit_factor,
            "avg_holding_days": avg_holding_days,
        }

    def _create_error_result(
        self,
        strategy: StrategyDefinition,
        error_message: str,
        execution_time: float = 0.0
    ) -> BacktestResult:
        """에러 결과 생성"""
        return BacktestResult(
            strategy_id=0,
            strategy_name=strategy.name,
            start_date=self.config.start_date,
            end_date=self.config.end_date,
            initial_capital=self.config.initial_capital,
            final_value=self.config.initial_capital,
            total_return=Decimal("0"),
            mdd=Decimal("0"),
            error_message=error_message,
            execution_time_seconds=execution_time
        )
