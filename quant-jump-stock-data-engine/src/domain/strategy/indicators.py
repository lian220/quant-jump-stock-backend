"""
순수 기술적 지표 계산 함수

외부 의존성 없이 pandas/numpy만 사용하는 순수 계산 함수들입니다.
DB 접근, API 호출 등 사이드 이펙트가 없습니다.

사용 예시:
    from src.domain.strategy.indicators import calculate_sma, calculate_rsi

    sma_20 = calculate_sma(close_prices, period=20)
    rsi_14 = calculate_rsi(close_prices, period=14)
"""
import numpy as np
import pandas as pd
from typing import Tuple, Optional

from src.domain.common.exceptions import InsufficientDataError, IndicatorError


def calculate_sma(series: pd.Series, period: int) -> pd.Series:
    """
    단순 이동평균 (Simple Moving Average)

    Args:
        series: 가격 시리즈
        period: 이동평균 기간

    Returns:
        SMA 시리즈

    Raises:
        InsufficientDataError: 데이터가 부족한 경우
        IndicatorError: 잘못된 파라미터
    """
    if period <= 0:
        raise IndicatorError("Period must be positive", "sma")
    if len(series) < period:
        raise InsufficientDataError(
            f"Insufficient data for SMA({period})",
            required=period,
            actual=len(series)
        )

    return series.rolling(window=period, min_periods=period).mean()


def calculate_ema(series: pd.Series, period: int, adjust: bool = False) -> pd.Series:
    """
    지수 이동평균 (Exponential Moving Average)

    Args:
        series: 가격 시리즈
        period: 이동평균 기간
        adjust: EWM 조정 옵션

    Returns:
        EMA 시리즈
    """
    if period <= 0:
        raise IndicatorError("Period must be positive", "ema")
    if len(series) < period:
        raise InsufficientDataError(
            f"Insufficient data for EMA({period})",
            required=period,
            actual=len(series)
        )

    return series.ewm(span=period, adjust=adjust).mean()


def calculate_rsi(series: pd.Series, period: int = 14) -> pd.Series:
    """
    상대강도지수 (Relative Strength Index)

    Args:
        series: 가격 시리즈
        period: RSI 기간 (기본 14)

    Returns:
        RSI 시리즈 (0-100)
    """
    if period <= 0:
        raise IndicatorError("Period must be positive", "rsi")
    if len(series) < period + 1:
        raise InsufficientDataError(
            f"Insufficient data for RSI({period})",
            required=period + 1,
            actual=len(series)
        )

    delta = series.diff()

    gain = delta.where(delta > 0, 0.0)
    loss = (-delta).where(delta < 0, 0.0)

    avg_gain = gain.rolling(window=period, min_periods=period).mean()
    avg_loss = loss.rolling(window=period, min_periods=period).mean()

    # 0으로 나누기 방지
    epsilon = 1e-10
    rs = avg_gain / (avg_loss + epsilon)

    # 무한대 처리
    rs = rs.replace([np.inf, -np.inf], np.nan)

    rsi = 100 - (100 / (1 + rs))

    # RSI를 0-100 범위로 클리핑
    return rsi.clip(0, 100)


def calculate_macd(
    series: pd.Series,
    short_period: int = 12,
    long_period: int = 26,
    signal_period: int = 9
) -> Tuple[pd.Series, pd.Series, pd.Series]:
    """
    MACD (Moving Average Convergence Divergence)

    Args:
        series: 가격 시리즈
        short_period: 단기 EMA 기간 (기본 12)
        long_period: 장기 EMA 기간 (기본 26)
        signal_period: 시그널 기간 (기본 9)

    Returns:
        Tuple[MACD선, 시그널선, 히스토그램]
    """
    if short_period >= long_period:
        raise IndicatorError("Short period must be less than long period", "macd")
    if len(series) < long_period + signal_period:
        raise InsufficientDataError(
            f"Insufficient data for MACD",
            required=long_period + signal_period,
            actual=len(series)
        )

    short_ema = series.ewm(span=short_period, adjust=False).mean()
    long_ema = series.ewm(span=long_period, adjust=False).mean()

    macd_line = short_ema - long_ema
    signal_line = macd_line.ewm(span=signal_period, adjust=False).mean()
    histogram = macd_line - signal_line

    return macd_line, signal_line, histogram


def calculate_bollinger_bands(
    series: pd.Series,
    period: int = 20,
    std_dev: float = 2.0
) -> Tuple[pd.Series, pd.Series, pd.Series]:
    """
    볼린저 밴드 (Bollinger Bands)

    Args:
        series: 가격 시리즈
        period: 이동평균 기간 (기본 20)
        std_dev: 표준편차 배수 (기본 2.0)

    Returns:
        Tuple[상단밴드, 중간밴드, 하단밴드]
    """
    if period <= 0:
        raise IndicatorError("Period must be positive", "bollinger")
    if std_dev <= 0:
        raise IndicatorError("Standard deviation multiplier must be positive", "bollinger")
    if len(series) < period:
        raise InsufficientDataError(
            f"Insufficient data for Bollinger Bands({period})",
            required=period,
            actual=len(series)
        )

    middle_band = series.rolling(window=period, min_periods=period).mean()
    rolling_std = series.rolling(window=period, min_periods=period).std()

    upper_band = middle_band + (rolling_std * std_dev)
    lower_band = middle_band - (rolling_std * std_dev)

    return upper_band, middle_band, lower_band


def calculate_atr(
    high: pd.Series,
    low: pd.Series,
    close: pd.Series,
    period: int = 14
) -> pd.Series:
    """
    평균 실제 범위 (Average True Range)

    Args:
        high: 고가 시리즈
        low: 저가 시리즈
        close: 종가 시리즈
        period: ATR 기간 (기본 14)

    Returns:
        ATR 시리즈
    """
    if period <= 0:
        raise IndicatorError("Period must be positive", "atr")
    if len(close) < period + 1:
        raise InsufficientDataError(
            f"Insufficient data for ATR({period})",
            required=period + 1,
            actual=len(close)
        )

    prev_close = close.shift(1)

    tr1 = high - low
    tr2 = (high - prev_close).abs()
    tr3 = (low - prev_close).abs()

    true_range = pd.concat([tr1, tr2, tr3], axis=1).max(axis=1)
    atr = true_range.rolling(window=period, min_periods=period).mean()

    return atr


def calculate_stochastic(
    high: pd.Series,
    low: pd.Series,
    close: pd.Series,
    k_period: int = 14,
    d_period: int = 3
) -> Tuple[pd.Series, pd.Series]:
    """
    스토캐스틱 오실레이터

    Args:
        high: 고가 시리즈
        low: 저가 시리즈
        close: 종가 시리즈
        k_period: %K 기간 (기본 14)
        d_period: %D 기간 (기본 3)

    Returns:
        Tuple[%K, %D]
    """
    if k_period <= 0 or d_period <= 0:
        raise IndicatorError("Periods must be positive", "stochastic")

    lowest_low = low.rolling(window=k_period, min_periods=k_period).min()
    highest_high = high.rolling(window=k_period, min_periods=k_period).max()

    # 0으로 나누기 방지
    epsilon = 1e-10
    k = 100 * ((close - lowest_low) / (highest_high - lowest_low + epsilon))
    d = k.rolling(window=d_period, min_periods=d_period).mean()

    return k.clip(0, 100), d.clip(0, 100)


def calculate_obv(close: pd.Series, volume: pd.Series) -> pd.Series:
    """
    거래량 균형 (On-Balance Volume)

    Args:
        close: 종가 시리즈
        volume: 거래량 시리즈

    Returns:
        OBV 시리즈
    """
    if len(close) != len(volume):
        raise IndicatorError("Close and volume series must have same length", "obv")

    price_change = close.diff()

    obv = pd.Series(index=close.index, dtype=float)
    obv.iloc[0] = 0

    for i in range(1, len(close)):
        if price_change.iloc[i] > 0:
            obv.iloc[i] = obv.iloc[i - 1] + volume.iloc[i]
        elif price_change.iloc[i] < 0:
            obv.iloc[i] = obv.iloc[i - 1] - volume.iloc[i]
        else:
            obv.iloc[i] = obv.iloc[i - 1]

    return obv


def crosses_above(series1: pd.Series, series2: pd.Series) -> pd.Series:
    """
    상향 돌파 감지

    Args:
        series1: 첫 번째 시리즈 (돌파하는 쪽)
        series2: 두 번째 시리즈 (돌파당하는 쪽)

    Returns:
        상향 돌파 여부 (Boolean Series)
    """
    prev_below = series1.shift(1) <= series2.shift(1)
    curr_above = series1 > series2
    return prev_below & curr_above


def crosses_below(series1: pd.Series, series2: pd.Series) -> pd.Series:
    """
    하향 돌파 감지

    Args:
        series1: 첫 번째 시리즈 (돌파하는 쪽)
        series2: 두 번째 시리즈 (돌파당하는 쪽)

    Returns:
        하향 돌파 여부 (Boolean Series)
    """
    prev_above = series1.shift(1) >= series2.shift(1)
    curr_below = series1 < series2
    return prev_above & curr_below
