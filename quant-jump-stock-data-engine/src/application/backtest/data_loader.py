"""
Backtest Data Loader

백테스트용 시장 데이터 로더.
yfinance 또는 로컬 데이터소스에서 OHLCV 데이터를 로드합니다.

Note:
    MongoDB 로더는 data_loader_mongo.py의 MongoDataLoader를 사용하세요.
"""

import logging
from abc import ABC, abstractmethod
from datetime import date, datetime, timedelta
from typing import Dict, List, Optional

import pandas as pd

logger = logging.getLogger(__name__)


class DataLoader(ABC):
    """
    데이터 로더 추상 클래스

    다양한 데이터 소스 (yfinance, MongoDB, CSV 등)를 지원하기 위한 인터페이스.
    """

    @abstractmethod
    def load(
        self,
        symbols: List[str],
        start_date: date,
        end_date: date
    ) -> Dict[str, pd.DataFrame]:
        """
        여러 종목의 OHLCV 데이터 로드

        Args:
            symbols: 종목 코드 목록
            start_date: 시작일
            end_date: 종료일

        Returns:
            {symbol: DataFrame} - 각 DataFrame은 OHLCV 컬럼을 가짐
            DataFrame columns: open, high, low, close, volume
            DataFrame index: DatetimeIndex
        """
        pass

    @abstractmethod
    def load_single(
        self,
        symbol: str,
        start_date: date,
        end_date: date
    ) -> pd.DataFrame:
        """
        단일 종목 데이터 로드

        Args:
            symbol: 종목 코드
            start_date: 시작일
            end_date: 종료일

        Returns:
            DataFrame with OHLCV columns
        """
        pass


class YFinanceDataLoader(DataLoader):
    """
    yfinance를 사용한 데이터 로더

    Yahoo Finance에서 OHLCV 데이터를 로드합니다.
    한국 주식의 경우 종목코드 뒤에 .KS (KOSPI) 또는 .KQ (KOSDAQ)를 붙입니다.
    """

    def __init__(self, market: str = "KS", add_buffer_days: int = 50):
        """
        Args:
            market: 시장 코드 (KS: KOSPI, KQ: KOSDAQ)
            add_buffer_days: 지표 계산을 위한 추가 버퍼 일수
        """
        self.market = market
        self.add_buffer_days = add_buffer_days

    def _get_yahoo_symbol(self, symbol: str) -> str:
        """종목 코드를 Yahoo Finance 형식으로 변환"""
        # 이미 .KS 또는 .KQ가 붙어있으면 그대로 반환
        if symbol.endswith(".KS") or symbol.endswith(".KQ"):
            return symbol

        # 미국 주식 (알파벳으로만 구성)
        if symbol.isalpha():
            return symbol

        # 한국 주식
        return f"{symbol}.{self.market}"

    def load(
        self,
        symbols: List[str],
        start_date: date,
        end_date: date
    ) -> Dict[str, pd.DataFrame]:
        """여러 종목 데이터 로드"""
        result = {}

        for symbol in symbols:
            try:
                df = self.load_single(symbol, start_date, end_date)
                if not df.empty:
                    result[symbol] = df
                else:
                    logger.warning(f"No data found for {symbol}")
            except Exception as e:
                logger.error(f"Failed to load {symbol}: {e}")

        return result

    def load_single(
        self,
        symbol: str,
        start_date: date,
        end_date: date
    ) -> pd.DataFrame:
        """단일 종목 데이터 로드"""
        try:
            import yfinance as yf
        except ImportError:
            raise ImportError("yfinance is required. Install with: pip install yfinance")

        yahoo_symbol = self._get_yahoo_symbol(symbol)

        # 지표 계산을 위한 버퍼 추가
        buffered_start = start_date - timedelta(days=self.add_buffer_days)

        logger.info(f"Loading {yahoo_symbol} from {buffered_start} to {end_date}")

        try:
            ticker = yf.Ticker(yahoo_symbol)
            df = ticker.history(
                start=buffered_start.isoformat(),
                end=(end_date + timedelta(days=1)).isoformat(),  # end_date 포함
                auto_adjust=True
            )

            if df.empty:
                logger.warning(f"No data returned for {yahoo_symbol}")
                return pd.DataFrame()

            # 컬럼명 소문자로 변환
            df.columns = df.columns.str.lower()

            # 필요한 컬럼만 선택
            required_columns = ["open", "high", "low", "close", "volume"]
            available_columns = [c for c in required_columns if c in df.columns]

            if not available_columns:
                logger.warning(f"No OHLCV columns found for {yahoo_symbol}")
                return pd.DataFrame()

            df = df[available_columns]

            # 인덱스를 날짜로만 (시간 제거)
            df.index = pd.to_datetime(df.index).date
            df.index = pd.DatetimeIndex(df.index)

            logger.info(f"Loaded {len(df)} rows for {symbol}")

            return df

        except Exception as e:
            logger.error(f"Error loading {yahoo_symbol}: {e}")
            raise


class PandasDataLoader(DataLoader):
    """
    pandas DataFrame을 직접 사용하는 데이터 로더

    테스트나 이미 로드된 데이터를 사용할 때 유용합니다.
    """

    def __init__(self, data: Dict[str, pd.DataFrame]):
        """
        Args:
            data: {symbol: DataFrame} 형태의 데이터
        """
        self._data = data

    def load(
        self,
        symbols: List[str],
        start_date: date,
        end_date: date
    ) -> Dict[str, pd.DataFrame]:
        """데이터 필터링하여 반환"""
        result = {}

        for symbol in symbols:
            if symbol in self._data:
                df = self._filter_by_date(self._data[symbol], start_date, end_date)
                if not df.empty:
                    result[symbol] = df

        return result

    def load_single(
        self,
        symbol: str,
        start_date: date,
        end_date: date
    ) -> pd.DataFrame:
        """단일 종목 데이터 필터링"""
        if symbol not in self._data:
            return pd.DataFrame()

        return self._filter_by_date(self._data[symbol], start_date, end_date)

    def _filter_by_date(
        self,
        df: pd.DataFrame,
        start_date: date,
        end_date: date
    ) -> pd.DataFrame:
        """날짜 범위로 필터링"""
        if df.empty:
            return df

        # 인덱스가 DatetimeIndex가 아니면 변환
        if not isinstance(df.index, pd.DatetimeIndex):
            df.index = pd.to_datetime(df.index)

        start_dt = datetime.combine(start_date, datetime.min.time())
        end_dt = datetime.combine(end_date, datetime.max.time())

        return df[(df.index >= start_dt) & (df.index <= end_dt)]
