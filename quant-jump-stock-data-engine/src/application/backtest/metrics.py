"""
Backtest Metrics Calculator

백테스트 성과 지표 계산 모듈.
CAGR, MDD, Sharpe Ratio 등 핵심 성과 지표를 계산합니다.

SCRUM-184: engine.py에서 분리하여 재사용성 및 테스트 용이성 향상
"""

import math
from dataclasses import dataclass
from datetime import date
from decimal import Decimal, ROUND_HALF_UP
from typing import List, Optional

import numpy as np


# 연간 거래일 수
TRADING_DAYS_PER_YEAR = 252

# 무위험 수익률 (한국 기준금리 근사)
DEFAULT_RISK_FREE_RATE = Decimal("0.03")  # 3%


@dataclass
class PerformanceMetrics:
    """
    백테스트 성과 지표 결과

    Attributes:
        total_return: 총 수익금액
        total_return_pct: 총 수익률 (%)
        cagr: 연평균 복합 성장률 (%)
        volatility: 연환산 변동성 (%)
        sharpe_ratio: 샤프 비율
        sortino_ratio: 소르티노 비율
        mdd: 최대 낙폭 (%)
    """
    total_return: Decimal
    total_return_pct: Decimal
    cagr: Decimal
    volatility: Optional[Decimal] = None
    sharpe_ratio: Optional[Decimal] = None
    sortino_ratio: Optional[Decimal] = None
    mdd: Decimal = Decimal("0")


@dataclass
class TradeMetrics:
    """
    거래 통계 지표

    Attributes:
        total_trades: 총 거래 수
        winning_trades: 수익 거래 수
        losing_trades: 손실 거래 수
        win_rate: 승률 (%)
        avg_win: 평균 수익
        avg_loss: 평균 손실
        largest_win: 최대 수익
        largest_loss: 최대 손실
        profit_factor: 수익 팩터
        avg_holding_days: 평균 보유 기간 (일)
    """
    total_trades: int = 0
    winning_trades: int = 0
    losing_trades: int = 0
    win_rate: Optional[Decimal] = None
    avg_win: Optional[Decimal] = None
    avg_loss: Optional[Decimal] = None
    largest_win: Optional[Decimal] = None
    largest_loss: Optional[Decimal] = None
    profit_factor: Optional[Decimal] = None
    avg_holding_days: Optional[Decimal] = None


def calculate_cagr(
    initial_value: Decimal,
    final_value: Decimal,
    start_date: date,
    end_date: date
) -> Decimal:
    """
    CAGR (Compound Annual Growth Rate) 계산

    연평균 복합 성장률을 계산합니다.
    CAGR = (final_value / initial_value)^(1/years) - 1

    Args:
        initial_value: 초기 자산
        final_value: 최종 자산
        start_date: 시작일
        end_date: 종료일

    Returns:
        CAGR (%)

    Examples:
        >>> calculate_cagr(Decimal("1000000"), Decimal("1210000"),
        ...                date(2022, 1, 1), date(2024, 1, 1))
        Decimal('10.00')  # 연 10% 성장
    """
    if initial_value <= 0:
        return Decimal("0")

    days = (end_date - start_date).days
    if days <= 0:
        return Decimal("0")

    years = Decimal(str(days)) / Decimal("365")

    # 수익률 계산
    total_return_pct = ((final_value / initial_value) - 1) * 100

    if total_return_pct <= Decimal("-100"):
        return Decimal("-100")

    # CAGR 계산: (1 + r)^(1/years) - 1
    ratio = final_value / initial_value

    if ratio <= 0:
        return Decimal("-100")

    try:
        cagr = (Decimal(str(float(ratio) ** (1 / float(years)))) - 1) * 100
        return cagr.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    except (ValueError, OverflowError):
        return Decimal("0")


def calculate_mdd(equity_values: List[Decimal]) -> Decimal:
    """
    MDD (Maximum Drawdown) 계산

    고점 대비 최대 낙폭을 계산합니다.

    Args:
        equity_values: 일별 자산 가치 리스트

    Returns:
        MDD (%, 음수로 반환)

    Examples:
        >>> values = [Decimal("100"), Decimal("120"), Decimal("90"), Decimal("110")]
        >>> calculate_mdd(values)
        Decimal('-25.00')  # 120에서 90으로 25% 하락
    """
    if not equity_values or len(equity_values) < 2:
        return Decimal("0")

    max_drawdown = Decimal("0")
    peak = equity_values[0]

    for value in equity_values:
        if value > peak:
            peak = value

        if peak > 0:
            drawdown = ((value / peak) - 1) * 100
            if drawdown < max_drawdown:
                max_drawdown = drawdown

    return max_drawdown.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def calculate_volatility(daily_returns: List[float]) -> Optional[Decimal]:
    """
    연환산 변동성 계산

    일간 수익률의 표준편차를 연환산합니다.
    Volatility = daily_std * sqrt(252)

    Args:
        daily_returns: 일간 수익률 리스트 (소수점, 예: 0.01 = 1%)

    Returns:
        연환산 변동성 (%), None if insufficient data
    """
    if not daily_returns or len(daily_returns) < 2:
        return None

    returns_array = np.array(daily_returns)
    daily_std = float(np.std(returns_array, ddof=1))  # 표본 표준편차
    annual_vol = daily_std * math.sqrt(TRADING_DAYS_PER_YEAR)

    return Decimal(str(annual_vol * 100)).quantize(
        Decimal("0.01"), rounding=ROUND_HALF_UP
    )


def calculate_sharpe_ratio(
    cagr: Decimal,
    volatility: Decimal,
    risk_free_rate: Decimal = DEFAULT_RISK_FREE_RATE
) -> Optional[Decimal]:
    """
    Sharpe Ratio 계산

    위험 조정 수익률을 계산합니다.
    Sharpe = (return - risk_free_rate) / volatility

    Args:
        cagr: 연평균 수익률 (%)
        volatility: 연환산 변동성 (%)
        risk_free_rate: 무위험 수익률 (소수점, 예: 0.03 = 3%)

    Returns:
        Sharpe Ratio, None if volatility is zero

    Examples:
        >>> calculate_sharpe_ratio(Decimal("15"), Decimal("20"))
        Decimal('0.60')  # (15% - 3%) / 20% = 0.6
    """
    if volatility is None or volatility <= 0:
        return None

    # CAGR을 소수점으로 변환 (15% -> 0.15)
    annual_return = cagr / 100
    annual_vol = volatility / 100

    excess_return = annual_return - risk_free_rate
    sharpe = excess_return / annual_vol

    return Decimal(str(float(sharpe))).quantize(
        Decimal("0.01"), rounding=ROUND_HALF_UP
    )


def calculate_sortino_ratio(
    cagr: Decimal,
    daily_returns: List[float],
    risk_free_rate: Decimal = DEFAULT_RISK_FREE_RATE
) -> Optional[Decimal]:
    """
    Sortino Ratio 계산

    하방 위험만 고려한 위험 조정 수익률을 계산합니다.
    Sortino = (return - risk_free_rate) / downside_volatility

    Args:
        cagr: 연평균 수익률 (%)
        daily_returns: 일간 수익률 리스트
        risk_free_rate: 무위험 수익률

    Returns:
        Sortino Ratio, None if no negative returns
    """
    if not daily_returns:
        return None

    returns_array = np.array(daily_returns)
    negative_returns = returns_array[returns_array < 0]

    if len(negative_returns) == 0:
        return None

    # 하방 변동성 (연환산)
    downside_std = float(np.std(negative_returns, ddof=1)) * math.sqrt(TRADING_DAYS_PER_YEAR)

    if downside_std <= 0:
        return None

    annual_return = float(cagr) / 100
    excess_return = annual_return - float(risk_free_rate)
    sortino = excess_return / downside_std

    return Decimal(str(sortino)).quantize(
        Decimal("0.01"), rounding=ROUND_HALF_UP
    )


def calculate_daily_returns(equity_values: List[Decimal]) -> List[float]:
    """
    일간 수익률 계산

    Args:
        equity_values: 일별 자산 가치 리스트

    Returns:
        일간 수익률 리스트 (소수점)
    """
    if len(equity_values) < 2:
        return []

    daily_returns = []
    for i in range(1, len(equity_values)):
        prev_value = float(equity_values[i - 1])
        curr_value = float(equity_values[i])

        if prev_value > 0:
            ret = (curr_value - prev_value) / prev_value
            daily_returns.append(ret)

    return daily_returns


def calculate_performance_metrics(
    initial_capital: Decimal,
    final_value: Decimal,
    start_date: date,
    end_date: date,
    equity_values: List[Decimal],
    risk_free_rate: Decimal = DEFAULT_RISK_FREE_RATE
) -> PerformanceMetrics:
    """
    종합 성과 지표 계산

    Args:
        initial_capital: 초기 자본
        final_value: 최종 자산
        start_date: 시작일
        end_date: 종료일
        equity_values: 일별 자산 가치 리스트
        risk_free_rate: 무위험 수익률

    Returns:
        PerformanceMetrics 객체
    """
    # 기본 수익률
    total_return = final_value - initial_capital
    total_return_pct = Decimal("0")
    if initial_capital > 0:
        total_return_pct = ((final_value / initial_capital) - 1) * 100

    # CAGR
    cagr = calculate_cagr(initial_capital, final_value, start_date, end_date)

    # MDD
    mdd = calculate_mdd(equity_values)

    # 일간 수익률
    daily_returns = calculate_daily_returns(equity_values)

    # 변동성
    volatility = calculate_volatility(daily_returns)

    # Sharpe Ratio
    sharpe_ratio = None
    if volatility is not None:
        sharpe_ratio = calculate_sharpe_ratio(cagr, volatility, risk_free_rate)

    # Sortino Ratio
    sortino_ratio = calculate_sortino_ratio(cagr, daily_returns, risk_free_rate)

    return PerformanceMetrics(
        total_return=total_return,
        total_return_pct=total_return_pct.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP),
        cagr=cagr,
        volatility=volatility,
        sharpe_ratio=sharpe_ratio,
        sortino_ratio=sortino_ratio,
        mdd=mdd
    )


def calculate_trade_metrics(
    trades: List[dict],
) -> TradeMetrics:
    """
    거래 통계 지표 계산

    FIFO 방식으로 매수/매도를 매칭하여 손익을 계산합니다.

    Args:
        trades: 거래 기록 리스트 (각 거래는 dict with keys:
                symbol, trade_type, trade_date, price, quantity, commission)

    Returns:
        TradeMetrics 객체
    """
    if not trades:
        return TradeMetrics()

    # FIFO 매칭을 위한 매수 기록
    buy_trades_map: dict[str, list] = {}

    wins: List[Decimal] = []
    losses: List[Decimal] = []
    holding_days: List[int] = []

    for trade in trades:
        trade_type = trade.get("trade_type", "")
        symbol = trade.get("symbol", "")

        if trade_type == "buy":
            if symbol not in buy_trades_map:
                buy_trades_map[symbol] = []
            buy_trades_map[symbol].append(trade)

        elif trade_type == "sell":
            if symbol in buy_trades_map and buy_trades_map[symbol]:
                buy_trade = buy_trades_map[symbol].pop(0)

                # PnL 계산
                sell_price = Decimal(str(trade.get("price", 0)))
                buy_price = Decimal(str(buy_trade.get("price", 0)))
                quantity = int(trade.get("quantity", 0))
                sell_commission = Decimal(str(trade.get("commission", 0)))
                buy_commission = Decimal(str(buy_trade.get("commission", 0)))

                pnl = (sell_price - buy_price) * quantity
                pnl -= (sell_commission + buy_commission)

                if pnl > 0:
                    wins.append(pnl)
                else:
                    losses.append(pnl)

                # 보유 기간
                sell_date = trade.get("trade_date")
                buy_date = buy_trade.get("trade_date")
                if sell_date and buy_date:
                    days_held = (sell_date - buy_date).days
                    holding_days.append(days_held)

    # 통계 계산
    total_trades = len(wins) + len(losses)
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
        avg_win = (sum(wins) / len(wins)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
        largest_win = max(wins).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)

    if losses:
        avg_loss = (sum(losses) / len(losses)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
        largest_loss = min(losses).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)

    # Profit Factor
    profit_factor = None
    gross_profit = sum(wins) if wins else Decimal("0")
    gross_loss = abs(sum(losses)) if losses else Decimal("0")

    if gross_loss > 0:
        profit_factor = (gross_profit / gross_loss).quantize(
            Decimal("0.01"), rounding=ROUND_HALF_UP
        )
    elif gross_profit > 0:
        profit_factor = Decimal("999.99")

    # 평균 보유 기간
    avg_holding_days = None
    if holding_days:
        avg_holding_days = Decimal(str(sum(holding_days) / len(holding_days))).quantize(
            Decimal("0.1"), rounding=ROUND_HALF_UP
        )

    return TradeMetrics(
        total_trades=total_trades,
        winning_trades=winning_trades,
        losing_trades=losing_trades,
        win_rate=win_rate,
        avg_win=avg_win,
        avg_loss=avg_loss,
        largest_win=largest_win,
        largest_loss=largest_loss,
        profit_factor=profit_factor,
        avg_holding_days=avg_holding_days
    )
