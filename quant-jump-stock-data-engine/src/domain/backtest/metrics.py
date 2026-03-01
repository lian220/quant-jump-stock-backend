"""
Performance Metrics Calculator - 백테스트 성과 지표 계산기

백테스트 결과에서 다양한 성과 지표를 계산합니다.

주요 기능:
- 기본 지표: CAGR, MDD, Sharpe Ratio, Win Rate
- 추가 지표: Profit Factor, Expectancy, Kelly Percentage
- 변동성: Volatility, Sortino Ratio, Calmar Ratio
- 벤치마크: Benchmark Return, Beta, Alpha
- 거래 분석: 평균 보유 기간, 최대 수익/손실 거래, 손절/익절 횟수

사용 예시:
    calculator = MetricsCalculator()

    # 완료된 라운드트립 거래 내역으로부터 지표 계산
    metrics = calculator.calculate_all_metrics(
        trades=completed_trade_list,
        equity_curve=equity_values,
        initial_capital=1000000,
        risk_free_rate=0.03
    )
"""

import logging
import math
from dataclasses import dataclass, field
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP
from typing import List, Optional, Tuple

import numpy as np

logger = logging.getLogger(__name__)

# Beta 계산 시 벤치마크 분산 최소 임계값
MIN_BENCHMARK_VARIANCE = 1e-12


@dataclass
class CompletedTrade:
    """
    완료된 라운드트립 거래 기록 (진입 → 청산)

    models.py의 Trade(개별 매수/매도 이벤트)와 구분됩니다.

    Attributes:
        symbol: 종목 코드
        entry_date: 진입일
        exit_date: 청산일
        entry_price: 진입가
        exit_price: 청산가
        quantity: 수량
        pnl: 손익 금액
        pnl_pct: 손익률 (%)
        exit_reason: 청산 사유 (signal, stop_loss, take_profit, trailing_stop)
    """
    symbol: str
    entry_date: date
    exit_date: date
    entry_price: Decimal
    exit_price: Decimal
    quantity: int
    pnl: Decimal
    pnl_pct: Decimal
    exit_reason: str = "signal"

    @property
    def holding_days(self) -> int:
        """보유 기간 (일)"""
        return (self.exit_date - self.entry_date).days

    @property
    def is_winner(self) -> bool:
        """수익 거래 여부"""
        return self.pnl > 0

    @property
    def is_loser(self) -> bool:
        """손실 거래 여부"""
        return self.pnl < 0


# 하위 호환 alias
Trade = CompletedTrade


@dataclass
class TradeAnalysis:
    """
    거래 분석 결과

    Attributes:
        total_trades: 총 거래 수
        winning_trades: 수익 거래 수
        losing_trades: 손실 거래 수
        avg_holding_days: 평균 보유 기간
        max_winning_trade: 최대 수익 거래 금액
        max_losing_trade: 최대 손실 거래 금액
        stop_loss_count: 손절 횟수
        take_profit_count: 익절 횟수
        trailing_stop_count: 트레일링 스탑 횟수
        signal_exit_count: 시그널 청산 횟수
    """
    total_trades: int
    winning_trades: int
    losing_trades: int
    avg_holding_days: Decimal
    max_winning_trade: Decimal
    max_losing_trade: Decimal
    stop_loss_count: int
    take_profit_count: int
    trailing_stop_count: int
    signal_exit_count: int


@dataclass
class PerformanceMetrics:
    """
    성과 지표 결과

    Attributes:
        # 기본 지표
        cagr: 연평균 복리 수익률 (%)
        mdd: 최대 낙폭 (%, 음수)
        sharpe_ratio: 샤프 비율
        win_rate: 승률 (%)

        # 추가 지표
        profit_factor: 수익 대비 (총 수익 / 총 손실)
        expectancy: 기댓값 (거래당 평균 수익)
        kelly_percentage: 켈리 비율 (최적 투자 비중)

        # 변동성/리스크 지표
        volatility: 연환산 변동성 (%)
        sortino_ratio: 소르티노 비율
        calmar_ratio: 칼마 비율 (CAGR / |MDD|)

        # 거래 분석
        total_return: 총 수익률 (%)
        avg_win: 평균 수익 (%)
        avg_loss: 평균 손실 (%)
        risk_reward_ratio: 손익비
        best_trade: 최고 수익률 (%)
        worst_trade: 최저 수익률 (%)
        max_consecutive_wins: 최대 연속 승
        max_consecutive_losses: 최대 연속 패
        daily_returns: 일간 수익률 리스트

        trade_analysis: 거래 분석 결과
    """
    # 기본 지표
    cagr: Decimal
    mdd: Decimal
    sharpe_ratio: Optional[Decimal]
    win_rate: Decimal

    # 추가 지표
    profit_factor: Decimal
    expectancy: Decimal
    kelly_percentage: Decimal

    # 변동성/리스크 지표
    volatility: Optional[Decimal] = None
    sortino_ratio: Optional[Decimal] = None
    calmar_ratio: Optional[Decimal] = None

    # 상세 분석
    total_return: Decimal = Decimal("0")
    avg_win: Decimal = Decimal("0")
    avg_loss: Decimal = Decimal("0")
    risk_reward_ratio: Decimal = Decimal("0")
    best_trade: Optional[Decimal] = None
    worst_trade: Optional[Decimal] = None
    max_consecutive_wins: int = 0
    max_consecutive_losses: int = 0
    daily_returns: List[float] = field(default_factory=list)

    trade_analysis: Optional[TradeAnalysis] = None


class MetricsCalculator:
    """
    성과 지표 계산기

    백테스트 결과에서 다양한 성과 지표를 계산합니다.

    Args:
        risk_free_rate: 무위험 수익률 (연간, 기본: 0.03 = 3%)
        trading_days_per_year: 연간 거래일 수 (기본: 252)
    """

    def __init__(
        self,
        risk_free_rate: float = 0.03,
        trading_days_per_year: int = 252
    ):
        self.risk_free_rate = Decimal(str(risk_free_rate))
        self.trading_days_per_year = trading_days_per_year

    def calculate_all_metrics(
        self,
        trades: List[CompletedTrade],
        equity_curve: List[Decimal],
        initial_capital: Decimal,
        start_date: Optional[date] = None,
        end_date: Optional[date] = None
    ) -> PerformanceMetrics:
        """
        모든 성과 지표 계산

        Args:
            trades: 완료된 라운드트립 거래 내역 리스트
            equity_curve: 자산 곡선 (일별 자산 가치)
            initial_capital: 초기 자본
            start_date: 백테스트 시작일
            end_date: 백테스트 종료일

        Returns:
            PerformanceMetrics: 성과 지표 결과
        """
        # 거래 분석
        trade_analysis = self._analyze_trades(trades)

        # 기본 지표
        total_return = self._calculate_total_return(equity_curve, initial_capital)
        cagr = self._calculate_cagr(equity_curve, initial_capital, start_date, end_date)
        mdd = self._calculate_mdd(equity_curve)
        win_rate = self._calculate_win_rate(trades)

        # 일간 수익률
        daily_returns = self._calculate_daily_returns(equity_curve)

        # 변동성
        volatility = self._calculate_volatility(daily_returns)

        # Sharpe Ratio (변동성 기반)
        sharpe_ratio = None
        if volatility is not None and volatility > 0:
            sharpe_ratio = self._calculate_sharpe_from_vol(cagr, volatility)
        elif len(equity_curve) >= 2:
            # fallback: equity_curve에서 직접 계산
            sharpe_ratio = self._calculate_sharpe_ratio(equity_curve)

        # Sortino Ratio
        sortino_ratio = self._calculate_sortino_ratio(cagr, daily_returns)

        # Calmar Ratio
        calmar_ratio = self._calculate_calmar_ratio(cagr, mdd)

        # 수익/손실 분석
        avg_win, avg_loss = self._calculate_avg_win_loss(trades)
        risk_reward_ratio = self._calculate_risk_reward_ratio(avg_win, avg_loss)

        # 추가 지표
        profit_factor = self._calculate_profit_factor(trades)
        expectancy = self._calculate_expectancy(win_rate, avg_win, avg_loss)
        kelly_percentage = self._calculate_kelly_percentage(win_rate, avg_win, avg_loss)

        # 연속 승/패
        max_consecutive_wins, max_consecutive_losses = self._calculate_consecutive_streaks(trades)

        # 최고/최저 수익률
        best_trade, worst_trade = self._calculate_best_worst_trade(trades)

        logger.debug(
            f"[METRICS] trades={len(trades)}, cagr={cagr:.2f}%, "
            f"mdd={mdd:.2f}%, sharpe={sharpe_ratio}, "
            f"profit_factor={profit_factor:.2f}"
        )

        return PerformanceMetrics(
            cagr=cagr,
            mdd=mdd,
            sharpe_ratio=sharpe_ratio,
            win_rate=win_rate,
            profit_factor=profit_factor,
            expectancy=expectancy,
            kelly_percentage=kelly_percentage,
            volatility=volatility,
            sortino_ratio=sortino_ratio,
            calmar_ratio=calmar_ratio,
            total_return=total_return,
            avg_win=avg_win,
            avg_loss=avg_loss,
            risk_reward_ratio=risk_reward_ratio,
            best_trade=best_trade,
            worst_trade=worst_trade,
            max_consecutive_wins=max_consecutive_wins,
            max_consecutive_losses=max_consecutive_losses,
            daily_returns=daily_returns,
            trade_analysis=trade_analysis,
        )

    def _analyze_trades(self, trades: List[CompletedTrade]) -> TradeAnalysis:
        """거래 분석"""
        if not trades:
            return TradeAnalysis(
                total_trades=0,
                winning_trades=0,
                losing_trades=0,
                avg_holding_days=Decimal("0"),
                max_winning_trade=Decimal("0"),
                max_losing_trade=Decimal("0"),
                stop_loss_count=0,
                take_profit_count=0,
                trailing_stop_count=0,
                signal_exit_count=0
            )

        winners = [t for t in trades if t.is_winner]
        losers = [t for t in trades if t.is_loser]

        total_holding_days = sum(t.holding_days for t in trades)
        avg_holding_days = Decimal(str(total_holding_days)) / len(trades)

        max_win = max((t.pnl for t in winners), default=Decimal("0"))
        max_loss = min((t.pnl for t in losers), default=Decimal("0"))

        exit_reasons = {
            "stop_loss": sum(1 for t in trades if t.exit_reason == "stop_loss"),
            "take_profit": sum(1 for t in trades if t.exit_reason == "take_profit"),
            "trailing_stop": sum(1 for t in trades if t.exit_reason == "trailing_stop"),
            "signal": sum(1 for t in trades if t.exit_reason == "signal")
        }

        return TradeAnalysis(
            total_trades=len(trades),
            winning_trades=len(winners),
            losing_trades=len(losers),
            avg_holding_days=avg_holding_days,
            max_winning_trade=max_win,
            max_losing_trade=max_loss,
            stop_loss_count=exit_reasons["stop_loss"],
            take_profit_count=exit_reasons["take_profit"],
            trailing_stop_count=exit_reasons["trailing_stop"],
            signal_exit_count=exit_reasons["signal"]
        )

    def _calculate_total_return(
        self,
        equity_curve: List[Decimal],
        initial_capital: Decimal
    ) -> Decimal:
        """총 수익률 계산 (%)"""
        if not equity_curve or initial_capital <= 0:
            return Decimal("0")

        final_value = equity_curve[-1]
        return ((final_value - initial_capital) / initial_capital) * 100

    def _calculate_cagr(
        self,
        equity_curve: List[Decimal],
        initial_capital: Decimal,
        start_date: Optional[date] = None,
        end_date: Optional[date] = None
    ) -> Decimal:
        """
        CAGR (연평균 복리 수익률) 계산

        CAGR = (최종값 / 초기값)^(1/년수) - 1
        """
        if not equity_curve or initial_capital <= 0:
            return Decimal("0")

        final_value = equity_curve[-1]

        # 기간 계산
        if start_date and end_date:
            days = (end_date - start_date).days
        else:
            days = len(equity_curve)

        if days <= 0:
            return Decimal("0")

        years = Decimal(str(days)) / Decimal("365")

        if years <= 0:
            return Decimal("0")

        # final_value <= 0 이면 전액 손실 (-100%)
        if final_value <= 0:
            return Decimal("-100")

        ratio = float(final_value / initial_capital)
        years_float = float(years)

        try:
            cagr = (ratio ** (1 / years_float)) - 1
            return Decimal(str(cagr * 100))
        except (ValueError, ZeroDivisionError, OverflowError):
            return Decimal("0")

    def _calculate_mdd(self, equity_curve: List[Decimal]) -> Decimal:
        """
        MDD (최대 낙폭) 계산 — 음수로 반환

        MDD = min((현재값 / 고점) - 1) × 100
        """
        if not equity_curve or len(equity_curve) < 2:
            return Decimal("0")

        peak = equity_curve[0]
        max_drawdown = Decimal("0")

        for value in equity_curve:
            if value > peak:
                peak = value

            if peak > 0:
                drawdown = ((value / peak) - 1) * 100
                if drawdown < max_drawdown:
                    max_drawdown = drawdown

        return max_drawdown

    @staticmethod
    def _calculate_daily_returns(equity_curve: List[Decimal]) -> List[float]:
        """일간 수익률 계산"""
        if len(equity_curve) < 2:
            return []

        daily_returns = []
        for i in range(1, len(equity_curve)):
            prev_value = float(equity_curve[i - 1])
            curr_value = float(equity_curve[i])

            if prev_value > 0:
                ret = (curr_value - prev_value) / prev_value
                daily_returns.append(ret)

        return daily_returns

    def _calculate_volatility(self, daily_returns: List[float]) -> Optional[Decimal]:
        """
        연환산 변동성 계산

        Volatility = daily_std * sqrt(252)
        """
        if not daily_returns or len(daily_returns) < 2:
            return None

        returns_array = np.array(daily_returns)
        daily_std = float(np.std(returns_array, ddof=1))
        annual_vol = daily_std * math.sqrt(self.trading_days_per_year)

        return Decimal(str(annual_vol * 100)).quantize(
            Decimal("0.01"), rounding=ROUND_HALF_UP
        )

    def _calculate_sharpe_from_vol(
        self,
        cagr: Decimal,
        volatility: Decimal,
    ) -> Optional[Decimal]:
        """
        변동성 기반 Sharpe Ratio 계산

        Sharpe = (CAGR - risk_free_rate) / volatility
        """
        if volatility is None or volatility <= 0:
            return None

        annual_return = cagr / 100
        annual_vol = volatility / 100

        excess_return = annual_return - self.risk_free_rate
        sharpe = excess_return / annual_vol

        return Decimal(str(float(sharpe))).quantize(
            Decimal("0.01"), rounding=ROUND_HALF_UP
        )

    def _calculate_sharpe_ratio(
        self,
        equity_curve: List[Decimal],
    ) -> Decimal:
        """
        Sharpe Ratio 계산 (equity_curve에서 직접)

        Sharpe = (평균 수익률 - 무위험 수익률) / 수익률 표준편차
        """
        if len(equity_curve) < 2:
            return Decimal("0")

        returns = []
        for i in range(1, len(equity_curve)):
            if equity_curve[i - 1] > 0:
                daily_return = (equity_curve[i] - equity_curve[i - 1]) / equity_curve[i - 1]
                returns.append(float(daily_return))

        if not returns:
            return Decimal("0")

        avg_return = sum(returns) / len(returns)
        variance = sum((r - avg_return) ** 2 for r in returns) / len(returns)
        std_dev = math.sqrt(variance) if variance > 0 else 0

        if std_dev == 0:
            return Decimal("0")

        annualized_return = avg_return * self.trading_days_per_year
        annualized_std = std_dev * math.sqrt(self.trading_days_per_year)

        sharpe = (annualized_return - float(self.risk_free_rate)) / annualized_std
        return Decimal(str(round(sharpe, 2)))

    def _calculate_sortino_ratio(
        self,
        cagr: Decimal,
        daily_returns: List[float],
    ) -> Optional[Decimal]:
        """
        Sortino Ratio 계산

        Sortino = (return - risk_free_rate) / downside_volatility
        """
        if not daily_returns:
            return None

        returns_array = np.array(daily_returns)
        negative_returns = returns_array[returns_array < 0]

        if len(negative_returns) < 2:
            return None

        downside_std = float(np.std(negative_returns, ddof=1)) * math.sqrt(self.trading_days_per_year)

        if downside_std <= 0:
            return None

        annual_return = float(cagr) / 100
        excess_return = annual_return - float(self.risk_free_rate)
        sortino = excess_return / downside_std

        return Decimal(str(sortino)).quantize(
            Decimal("0.01"), rounding=ROUND_HALF_UP
        )

    @staticmethod
    def _calculate_calmar_ratio(
        cagr: Decimal,
        mdd: Decimal,
    ) -> Optional[Decimal]:
        """Calmar Ratio = CAGR / |MDD|"""
        if mdd == Decimal("0"):
            return None

        return cagr / abs(mdd)

    @staticmethod
    def _calculate_consecutive_streaks(
        trades: List[CompletedTrade],
    ) -> Tuple[int, int]:
        """최대 연속 승/패 계산"""
        max_wins = 0
        max_losses = 0
        current_wins = 0
        current_losses = 0

        for trade in trades:
            if trade.is_winner:
                current_wins += 1
                current_losses = 0
                max_wins = max(max_wins, current_wins)
            elif trade.is_loser:
                current_losses += 1
                current_wins = 0
                max_losses = max(max_losses, current_losses)
            else:
                current_wins = 0
                current_losses = 0

        return max_wins, max_losses

    @staticmethod
    def _calculate_best_worst_trade(
        trades: List[CompletedTrade],
    ) -> Tuple[Optional[Decimal], Optional[Decimal]]:
        """최고/최저 수익률 거래"""
        if not trades:
            return None, None

        pnl_pcts = [t.pnl_pct for t in trades]
        return max(pnl_pcts), min(pnl_pcts)

    def _calculate_win_rate(self, trades: List[CompletedTrade]) -> Decimal:
        """승률 계산"""
        if not trades:
            return Decimal("0")

        winners = sum(1 for t in trades if t.is_winner)
        return (Decimal(str(winners)) / len(trades)) * 100

    def _calculate_avg_win_loss(
        self,
        trades: List[CompletedTrade]
    ) -> tuple[Decimal, Decimal]:
        """평균 수익/손실 계산"""
        winners = [t for t in trades if t.is_winner]
        losers = [t for t in trades if t.is_loser]

        avg_win = Decimal("0")
        avg_loss = Decimal("0")

        if winners:
            avg_win = sum(t.pnl_pct for t in winners) / len(winners)

        if losers:
            avg_loss = abs(sum(t.pnl_pct for t in losers) / len(losers))

        return avg_win, avg_loss

    def _calculate_risk_reward_ratio(
        self,
        avg_win: Decimal,
        avg_loss: Decimal
    ) -> Decimal:
        """손익비 계산"""
        if avg_loss == 0:
            return Decimal("0")

        return avg_win / avg_loss

    def _calculate_profit_factor(self, trades: List[CompletedTrade]) -> Decimal:
        """
        Profit Factor 계산

        Profit Factor = 총 수익 / 총 손실
        >1이면 수익, <1이면 손실
        """
        if not trades:
            return Decimal("0")

        total_wins = sum(t.pnl for t in trades if t.is_winner)
        total_losses = abs(sum(t.pnl for t in trades if t.is_loser))

        if total_losses == 0:
            return Decimal("999") if total_wins > 0 else Decimal("0")

        return total_wins / total_losses

    def _calculate_expectancy(
        self,
        win_rate: Decimal,
        avg_win: Decimal,
        avg_loss: Decimal
    ) -> Decimal:
        """
        Expectancy (기댓값) 계산

        Expectancy = (승률 × 평균수익) - (패률 × 평균손실)
        """
        win_rate_ratio = win_rate / 100
        lose_rate_ratio = Decimal("1") - win_rate_ratio

        expectancy = (win_rate_ratio * avg_win) - (lose_rate_ratio * avg_loss)
        return expectancy

    def _calculate_kelly_percentage(
        self,
        win_rate: Decimal,
        avg_win: Decimal,
        avg_loss: Decimal
    ) -> Decimal:
        """
        Kelly Percentage 계산

        Kelly % = (승률 × 평균수익 - 패률 × 평균손실) / 평균수익
        """
        if avg_win == 0:
            return Decimal("0")

        win_rate_ratio = win_rate / 100
        lose_rate_ratio = Decimal("1") - win_rate_ratio

        kelly = (win_rate_ratio * avg_win - lose_rate_ratio * avg_loss) / avg_win
        return kelly * 100  # 퍼센트로 반환

    @staticmethod
    def from_trade_dicts(trade_dicts: List[dict]) -> List[CompletedTrade]:
        """딕셔너리 리스트에서 Trade 객체 리스트 생성"""
        trades = []
        for td in trade_dicts:
            trades.append(CompletedTrade(
                symbol=td.get("symbol", ""),
                entry_date=td.get("entry_date", date.today()),
                exit_date=td.get("exit_date", date.today()),
                entry_price=Decimal(str(td.get("entry_price", 0))),
                exit_price=Decimal(str(td.get("exit_price", 0))),
                quantity=td.get("quantity", 0),
                pnl=Decimal(str(td.get("pnl", 0))),
                pnl_pct=Decimal(str(td.get("pnl_pct", 0))),
                exit_reason=td.get("exit_reason", "signal")
            ))
        return trades

    def __repr__(self) -> str:
        return (
            f"MetricsCalculator("
            f"risk_free_rate={self.risk_free_rate * 100:.1f}%, "
            f"trading_days={self.trading_days_per_year}"
            f")"
        )


class BenchmarkCalculator:
    """벤치마크 관련 지표 계산기"""

    def __init__(self, risk_free_rate: float = 0.03):
        self.risk_free_rate = Decimal(str(risk_free_rate))

    def calculate_benchmark_return(
        self,
        benchmark_values: List[Decimal],
        start_date: date,
        end_date: date,
    ) -> Optional[Decimal]:
        """
        벤치마크 수익률 (CAGR)

        Args:
            benchmark_values: 일별 벤치마크 가격 리스트
            start_date: 시작일
            end_date: 종료일

        Returns:
            벤치마크 CAGR (%), None if insufficient data
        """
        if not benchmark_values or len(benchmark_values) < 2:
            return None

        initial_value = benchmark_values[0]
        final_value = benchmark_values[-1]

        if initial_value <= 0:
            return None

        days = (end_date - start_date).days
        if days <= 0:
            return None

        years = Decimal(str(days)) / Decimal("365")
        ratio = float(final_value / initial_value)

        try:
            cagr = (ratio ** (1 / float(years))) - 1
            return Decimal(str(cagr * 100)).quantize(
                Decimal("0.01"), rounding=ROUND_HALF_UP
            )
        except (ValueError, ZeroDivisionError, OverflowError):
            return None

    @staticmethod
    def calculate_beta(
        strategy_returns: List[float],
        benchmark_returns: List[float],
    ) -> Optional[Decimal]:
        """
        Beta 계산

        Beta = Cov(strategy, benchmark) / Var(benchmark)
        """
        if not strategy_returns or not benchmark_returns:
            return None

        min_len = min(len(strategy_returns), len(benchmark_returns))
        if min_len < 2:
            return None

        s_returns = np.array(strategy_returns[:min_len])
        b_returns = np.array(benchmark_returns[:min_len])

        benchmark_var = np.var(b_returns, ddof=1)
        if benchmark_var < MIN_BENCHMARK_VARIANCE:
            return None

        covariance = np.cov(s_returns, b_returns, ddof=1)[0][1]
        beta = covariance / benchmark_var

        return Decimal(str(beta)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)

    def calculate_alpha(
        self,
        strategy_cagr: Decimal,
        benchmark_cagr: Decimal,
        beta: Decimal,
    ) -> Decimal:
        """
        Jensen's Alpha 계산

        Alpha = strategy_return - (rf + beta * (benchmark_return - rf))
        """
        strategy_return = strategy_cagr / 100
        benchmark_return = benchmark_cagr / 100

        expected_return = self.risk_free_rate + beta * (benchmark_return - self.risk_free_rate)
        alpha = (strategy_return - expected_return) * 100

        return alpha.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)

    @staticmethod
    def calculate_daily_returns(equity_values: List[Decimal]) -> List[float]:
        """일간 수익률 계산 (벤치마크용 유틸리티)"""
        return MetricsCalculator._calculate_daily_returns(equity_values)
