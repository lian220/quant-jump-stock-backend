"""
Performance Metrics Calculator - 백테스트 성과 지표 계산기

백테스트 결과에서 다양한 성과 지표를 계산합니다.

주요 기능:
- 기본 지표: CAGR, MDD, Sharpe Ratio, Win Rate
- 추가 지표: Profit Factor, Expectancy, Kelly Percentage
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
from decimal import Decimal
from typing import List, Optional

logger = logging.getLogger(__name__)


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
        mdd: 최대 낙폭 (%)
        sharpe_ratio: 샤프 비율
        win_rate: 승률 (%)

        # 추가 지표
        profit_factor: 수익 대비 (총 수익 / 총 손실)
        expectancy: 기댓값 (거래당 평균 수익)
        kelly_percentage: 켈리 비율 (최적 투자 비중)

        # 상세 분석
        total_return: 총 수익률 (%)
        avg_win: 평균 수익 (%)
        avg_loss: 평균 손실 (%)
        risk_reward_ratio: 손익비
        trade_analysis: 거래 분석 결과
    """
    # 기본 지표
    cagr: Decimal
    mdd: Decimal
    sharpe_ratio: Decimal
    win_rate: Decimal

    # 추가 지표
    profit_factor: Decimal
    expectancy: Decimal
    kelly_percentage: Decimal

    # 상세 분석
    total_return: Decimal
    avg_win: Decimal
    avg_loss: Decimal
    risk_reward_ratio: Decimal
    trade_analysis: TradeAnalysis


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
            trades: 거래 내역 리스트
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
        sharpe_ratio = self._calculate_sharpe_ratio(equity_curve, initial_capital)
        win_rate = self._calculate_win_rate(trades)

        # 수익/손실 분석
        avg_win, avg_loss = self._calculate_avg_win_loss(trades)
        risk_reward_ratio = self._calculate_risk_reward_ratio(avg_win, avg_loss)

        # 추가 지표
        profit_factor = self._calculate_profit_factor(trades)
        expectancy = self._calculate_expectancy(win_rate, avg_win, avg_loss)
        kelly_percentage = self._calculate_kelly_percentage(win_rate, avg_win, avg_loss)

        logger.debug(
            f"[METRICS] trades={len(trades)}, cagr={cagr:.2f}%, "
            f"mdd={mdd:.2f}%, sharpe={sharpe_ratio:.2f}, "
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
            total_return=total_return,
            avg_win=avg_win,
            avg_loss=avg_loss,
            risk_reward_ratio=risk_reward_ratio,
            trade_analysis=trade_analysis
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
        """총 수익률 계산"""
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

        years = Decimal(str(days)) / Decimal("365")

        if years <= 0 or final_value <= 0:
            return Decimal("0")

        # CAGR 계산
        ratio = float(final_value / initial_capital)
        years_float = float(years)

        try:
            cagr = (ratio ** (1 / years_float)) - 1
            return Decimal(str(cagr * 100))
        except (ValueError, ZeroDivisionError):
            return Decimal("0")

    def _calculate_mdd(self, equity_curve: List[Decimal]) -> Decimal:
        """
        MDD (최대 낙폭) 계산

        MDD = max((고점 - 현재값) / 고점)
        """
        if not equity_curve:
            return Decimal("0")

        peak = equity_curve[0]
        max_drawdown = Decimal("0")

        for value in equity_curve:
            if value > peak:
                peak = value

            if peak > 0:
                drawdown = (peak - value) / peak
                if drawdown > max_drawdown:
                    max_drawdown = drawdown

        return max_drawdown * 100

    def _calculate_sharpe_ratio(
        self,
        equity_curve: List[Decimal],
        initial_capital: Decimal
    ) -> Decimal:
        """
        Sharpe Ratio 계산

        Sharpe = (평균 수익률 - 무위험 수익률) / 수익률 표준편차
        """
        if len(equity_curve) < 2:
            return Decimal("0")

        # 일별 수익률 계산
        returns = []
        for i in range(1, len(equity_curve)):
            if equity_curve[i - 1] > 0:
                daily_return = (equity_curve[i] - equity_curve[i - 1]) / equity_curve[i - 1]
                returns.append(float(daily_return))

        if not returns:
            return Decimal("0")

        # 평균 및 표준편차
        avg_return = sum(returns) / len(returns)
        variance = sum((r - avg_return) ** 2 for r in returns) / len(returns)
        std_dev = math.sqrt(variance) if variance > 0 else 0

        if std_dev == 0:
            return Decimal("0")

        # 연율화
        daily_rf = float(self.risk_free_rate) / self.trading_days_per_year
        annualized_return = avg_return * self.trading_days_per_year
        annualized_std = std_dev * math.sqrt(self.trading_days_per_year)

        sharpe = (annualized_return - float(self.risk_free_rate)) / annualized_std
        return Decimal(str(round(sharpe, 2)))

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

        거래당 기대 수익을 나타냅니다.
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

        최적의 투자 비중을 나타냅니다.
        음수면 해당 전략으로 투자하지 않는 것이 좋습니다.
        """
        if avg_win == 0:
            return Decimal("0")

        win_rate_ratio = win_rate / 100
        lose_rate_ratio = Decimal("1") - win_rate_ratio

        kelly = (win_rate_ratio * avg_win - lose_rate_ratio * avg_loss) / avg_win
        return kelly * 100  # 퍼센트로 반환

    @staticmethod
    def from_trade_dicts(trade_dicts: List[dict]) -> List[CompletedTrade]:
        """
        딕셔너리 리스트에서 Trade 객체 리스트 생성

        Args:
            trade_dicts: 거래 딕셔너리 리스트

        Returns:
            CompletedTrade 객체 리스트
        """
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
