from datetime import datetime, timedelta
from typing import List, Optional

import pandas as pd

from domain.common.exceptions import InsufficientDataError, IndicatorError
from domain.strategy.indicators import (
    calculate_sma,
    calculate_rsi,
    calculate_macd,
)

from .ports import PriceRepositoryPort

_PERIOD_DAYS = {"1m": 30, "3m": 90, "6m": 180, "1y": 365}
_LOOKBACK_DAYS = 120  # 지표 워밍업 버퍼(MACD 26+9, MA60 커버)


def period_to_dates(period: str, end_date: str) -> tuple[str, str]:
    if period not in _PERIOD_DAYS:
        raise ValueError(f"invalid period: {period}")
    end_dt = datetime.strptime(end_date, "%Y-%m-%d")
    start_dt = end_dt - timedelta(days=_PERIOD_DAYS[period])
    return start_dt.strftime("%Y-%m-%d"), end_date


def _series_or_none(values: pd.Series) -> List[Optional[float]]:
    return [None if pd.isna(v) else float(v) for v in values]


class PriceHistoryService:
    def __init__(self, repo: PriceRepositoryPort) -> None:
        self.repo = repo

    def build(self, ticker: str, period: str, today: str) -> dict:
        start_date, end_date = period_to_dates(period, today)
        query_start = (
            datetime.strptime(start_date, "%Y-%m-%d") - timedelta(days=_LOOKBACK_DAYS)
        ).strftime("%Y-%m-%d")

        rows = self.repo.get_ohlcv_series(ticker, query_start, end_date)
        if not rows:
            return {"ticker": ticker, "period": period, "candles": []}

        df = pd.DataFrame(rows)
        df["date"] = df["date"].astype(str)
        close = df["close"]

        df["ma20"] = self._safe(lambda: calculate_sma(close, 20), len(close))
        df["ma60"] = self._safe(lambda: calculate_sma(close, 60), len(close))
        df["rsi"] = self._safe(lambda: calculate_rsi(close, 14), len(close))
        df["macd"], df["signal"], df["histogram"] = self._macd(close)

        window = df[(df["date"] >= start_date) & (df["date"] <= end_date)]

        candles = []
        for _, r in window.iterrows():
            candles.append({
                "date": r["date"],
                "open": _none(r["open"]), "high": _none(r["high"]),
                "low": _none(r["low"]), "close": float(r["close"]),
                "volume": None if pd.isna(r["volume"]) else int(r["volume"]),
                "ma20": _none(r["ma20"]), "ma60": _none(r["ma60"]),
                "rsi": _none(r["rsi"]), "macd": _none(r["macd"]),
                "signal": _none(r["signal"]), "histogram": _none(r["histogram"]),
            })
        return {"ticker": ticker, "period": period, "candles": candles}

    @staticmethod
    def _safe(fn, n):
        try:
            return fn().tolist()
        except (InsufficientDataError, IndicatorError):
            return [None] * n

    @staticmethod
    def _macd(close):
        try:
            m, s, h = calculate_macd(close, 12, 26, 9)
            return _series_or_none(m), _series_or_none(s), _series_or_none(h)
        except (InsufficientDataError, IndicatorError):
            n = len(close)
            return [None] * n, [None] * n, [None] * n


def _none(v):
    return None if pd.isna(v) else float(v)
