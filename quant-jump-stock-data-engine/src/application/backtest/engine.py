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
import time
from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal
from typing import Dict, List, Optional, Set

import pandas as pd

from domain.strategy.models import StrategyDefinition, SignalType
from domain.strategy.interpreter import StrategyInterpreter
from domain.backtest.models import Portfolio, Position, ExitReason, TradeType
from domain.backtest.risk_manager import RiskManager

from .data_loader import DataLoader
from .result import BacktestResult, BacktestTrade, EquityCurvePoint
from .metrics import (
    calculate_cagr,
    calculate_mdd,
    calculate_daily_returns,
    calculate_volatility,
    calculate_sharpe_ratio,
    calculate_sortino_ratio,
    calculate_trade_metrics,
)

logger = logging.getLogger(__name__)


@dataclass
class BacktestConfig:
    """
    백테스트 설정

    Attributes:
        start_date: 백테스트 시작일
        end_date: 백테스트 종료일
        initial_capital: 초기 자본금
        tickers: 거래 대상 종목 목록
        commission_rate: 수수료율 (0.00015 = 0.015%)
        slippage_rate: 슬리피지율 (0.001 = 0.1%)
        max_positions: 최대 동시 보유 종목 수
        position_size_pct: 포지션당 비중 (0.1 = 10%)
        rebalance_frequency: 리밸런싱 주기 (daily, weekly, monthly)
    """
    start_date: date
    end_date: date
    initial_capital: Decimal = Decimal("10000000")
    tickers: List[str] = field(default_factory=list)
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
                f"{len(self.config.tickers)} symbols"
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
        symbols = self.config.tickers

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
        for symbol in self.config.tickers:
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
                "symbols": self.config.tickers,
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

        metrics.py 모듈의 함수들을 사용하여 성과 지표를 계산합니다.

        Returns:
            성과 지표 딕셔너리
        """
        # 기본 수익률
        total_return = self._portfolio.total_pnl
        total_return_pct = self._portfolio.total_pnl_pct

        # CAGR 계산 (metrics.py 사용)
        cagr = calculate_cagr(
            initial_value=self.config.initial_capital,
            final_value=self._portfolio.total_value,
            start_date=self.config.start_date,
            end_date=self.config.end_date
        )

        # 수익 곡선에서 자산 가치 추출
        equity_values = [p.equity for p in self._equity_curve]

        # 일간 수익률 계산 (metrics.py 사용)
        daily_returns = calculate_daily_returns(equity_values)

        # 변동성 계산 (metrics.py 사용)
        volatility = calculate_volatility(daily_returns)

        # Sharpe Ratio 계산 (metrics.py 사용)
        sharpe_ratio = None
        if volatility is not None:
            sharpe_ratio = calculate_sharpe_ratio(cagr, volatility)

        # Sortino Ratio 계산 (metrics.py 사용)
        sortino_ratio = calculate_sortino_ratio(cagr, daily_returns)

        # 거래 통계 계산 (metrics.py 사용)
        trades_for_metrics = [
            {
                "symbol": t.symbol,
                "trade_type": t.trade_type,
                "trade_date": t.trade_date,
                "price": t.price,
                "quantity": t.quantity,
                "commission": t.commission,
            }
            for t in trades
        ]
        trade_metrics = calculate_trade_metrics(trades_for_metrics)

        return {
            "total_return": total_return,
            "total_return_pct": total_return_pct,
            "cagr": cagr,
            "volatility": volatility,
            "sharpe_ratio": sharpe_ratio,
            "sortino_ratio": sortino_ratio,
            "total_trades": trade_metrics.total_trades,
            "winning_trades": trade_metrics.winning_trades,
            "losing_trades": trade_metrics.losing_trades,
            "win_rate": trade_metrics.win_rate,
            "avg_win": trade_metrics.avg_win,
            "avg_loss": trade_metrics.avg_loss,
            "largest_win": trade_metrics.largest_win,
            "largest_loss": trade_metrics.largest_loss,
            "profit_factor": trade_metrics.profit_factor,
            "avg_holding_days": trade_metrics.avg_holding_days,
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

    # ============================================================
    # Checkpoint Methods (증분 백테스트 지원)
    # ============================================================

    def create_checkpoint(self) -> Dict[str, any]:
        """
        현재 엔진 상태로 체크포인트 생성

        Note: equity_curve는 backtest_results에 저장되므로 체크포인트에 포함하지 않음.
              중복 저장 방지 및 저장 용량 최적화.

        Returns:
            체크포인트 데이터 딕셔너리 (equity_curve 제외)
        """
        if not self._portfolio:
            raise ValueError("Portfolio not initialized")

        # 포지션 직렬화
        positions = [
            {
                "symbol": pos.symbol,
                "entry_date": pos.entry_date.isoformat(),
                "entry_price": float(pos.entry_price),
                "quantity": pos.quantity,
                "current_price": float(pos.current_price),
                "highest_price": float(pos.highest_price),
                "lowest_price": float(pos.lowest_price),
                "cost_basis": float(pos.cost_basis),
            }
            for pos in self._portfolio.positions.values()
        ]

        return {
            "checkpoint_date": self._portfolio.current_date,
            "cash": self._portfolio.cash,
            "high_watermark": self._high_watermark,
            "positions": positions,
            "trade_count": len(self._portfolio.trades),
        }

    def resume_from_checkpoint(
        self,
        checkpoint_data: Dict[str, any],
        strategy: StrategyDefinition,
        equity_curve_data: Optional[List[Dict[str, any]]] = None
    ) -> None:
        """
        체크포인트에서 엔진 상태 복원

        Args:
            checkpoint_data: 체크포인트 데이터 (positions, cash, high_watermark)
            strategy: 전략 정의
            equity_curve_data: 수익 곡선 데이터 (backtest_results에서 조회)
        """
        from datetime import datetime

        # 포트폴리오 초기화
        self._portfolio = Portfolio(
            initial_capital=self.config.initial_capital
        )

        # 현금 및 거래 수 복원
        self._portfolio.cash = Decimal(str(checkpoint_data["cash"]))
        self._high_watermark = Decimal(str(checkpoint_data["high_watermark"]))

        # 포지션 복원
        for pos_data in checkpoint_data.get("positions", []):
            entry_date = datetime.fromisoformat(pos_data["entry_date"]).date()
            position = Position(
                symbol=pos_data["symbol"],
                entry_date=entry_date,
                entry_price=Decimal(str(pos_data["entry_price"])),
                quantity=pos_data["quantity"],
                current_price=Decimal(str(pos_data["current_price"])),
                highest_price=Decimal(str(pos_data["highest_price"])),
                lowest_price=Decimal(str(pos_data["lowest_price"])),
                cost_basis=Decimal(str(pos_data["cost_basis"])),
            )
            self._portfolio.positions[pos_data["symbol"]] = position

        # 수익 곡선 복원 (backtest_results에서 조회한 데이터)
        self._equity_curve = []
        if equity_curve_data:
            for point_data in equity_curve_data:
                point_date_str = point_data.get("date", "")
                # ISO format 또는 date 객체 처리
                if isinstance(point_date_str, str):
                    point_date = datetime.fromisoformat(point_date_str).date()
                else:
                    point_date = point_date_str
                point = EquityCurvePoint(
                    date=point_date,
                    equity=Decimal(str(point_data["equity"])),
                    cash=Decimal(str(point_data.get("cash", 0))),
                    positions_value=Decimal(str(point_data.get("positions_value", 0))),
                    drawdown_pct=Decimal(str(point_data.get("drawdown_pct", 0))),
                )
                self._equity_curve.append(point)

        # 리스크 매니저 초기화
        self._risk_manager = RiskManager.from_risk_management(
            strategy.risk_management
        )

        logger.info(
            f"Resumed from checkpoint: date={checkpoint_data.get('checkpoint_date')}, "
            f"cash={self._portfolio.cash}, positions={len(self._portfolio.positions)}, "
            f"equity_curve_points={len(self._equity_curve)}"
        )

    def run_incremental(
        self,
        strategy: StrategyDefinition,
        resume_from_date: date
    ) -> BacktestResult:
        """
        체크포인트 이후부터 증분 실행

        Args:
            strategy: 전략 정의
            resume_from_date: 재개 시작일 (이 날짜 다음날부터 실행)

        Returns:
            BacktestResult: 전체 백테스트 결과 (증분 포함)
        """
        start_time = time.time()

        try:
            # 1. 데이터 로드 (전체 기간)
            self._load_data()

            if not self._data:
                return self._create_error_result(
                    strategy, "No data loaded for any symbol"
                )

            # 2. 거래일 목록 생성 (resume_from_date 이후만)
            all_trading_dates = self._get_trading_dates()
            trading_dates = [
                d for d in all_trading_dates
                if d > resume_from_date
            ]

            if not trading_dates:
                logger.info(f"No new trading dates after {resume_from_date}")
                # 새 거래일이 없으면 현재 상태로 결과 생성
                execution_time = time.time() - start_time
                return self._create_result(strategy, execution_time)

            logger.info(
                f"Running incremental backtest: {strategy.name}, "
                f"from {resume_from_date} to {self.config.end_date}, "
                f"{len(trading_dates)} new trading days"
            )

            # 3. 일별 시뮬레이션 (새 거래일만)
            for current_date in trading_dates:
                self._process_day(strategy, current_date)

            # 4. 결과 생성
            execution_time = time.time() - start_time
            result = self._create_result(strategy, execution_time)

            logger.info(
                f"Incremental backtest completed: {strategy.name}, "
                f"Total return: {result.total_return:.2f}%, "
                f"New trades: {result.total_trades}"
            )

            return result

        except Exception as e:
            logger.exception(f"Incremental backtest failed: {strategy.name}")
            execution_time = time.time() - start_time
            return self._create_error_result(
                strategy, str(e), execution_time
            )

    def get_new_trades_since(self, trade_count_before: int) -> List[BacktestTrade]:
        """
        체크포인트 이후 새로 생긴 거래만 반환

        Args:
            trade_count_before: 체크포인트 시점의 거래 수

        Returns:
            새로 생긴 거래 목록
        """
        new_trades = self._portfolio.trades[trade_count_before:]

        return [
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
            for t in new_trades
        ]
