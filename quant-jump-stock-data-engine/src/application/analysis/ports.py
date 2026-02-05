"""
Analysis Ports

분석 서비스에서 사용하는 포트 인터페이스.
"""

from abc import ABC, abstractmethod
from typing import List, Dict, Any, Optional
from dataclasses import dataclass
from datetime import datetime


@dataclass
class StockInfo:
    """종목 정보"""
    ticker: str
    stock_name: str
    is_active: bool = True


@dataclass
class DailyPrice:
    """일별 가격 데이터"""
    ticker: str
    date: str
    close_price: float
    open_price: Optional[float] = None
    high_price: Optional[float] = None
    low_price: Optional[float] = None
    volume: Optional[int] = None


@dataclass
class TechnicalResult:
    """기술적 분석 결과"""
    ticker: str
    stock_name: str
    date: str
    sma20: float
    sma50: float
    rsi: float
    macd: float
    signal: float
    golden_cross: bool
    macd_buy_signal: bool
    is_recommended: bool


class StockRepositoryPort(ABC):
    """종목 저장소 포트"""

    @abstractmethod
    async def get_active_stocks(self) -> List[StockInfo]:
        """활성 종목 목록 조회"""
        pass


class PriceRepositoryPort(ABC):
    """가격 데이터 저장소 포트"""

    @abstractmethod
    async def get_daily_prices(
        self,
        start_date: str,
        end_date: str
    ) -> Dict[str, List[DailyPrice]]:
        """
        일별 가격 데이터 조회

        Returns:
            Dict[ticker, List[DailyPrice]]
        """
        pass


class AnalysisResultRepositoryPort(ABC):
    """분석 결과 저장소 포트"""

    @abstractmethod
    async def save_technical_result(self, result: TechnicalResult) -> None:
        """기술적 분석 결과 저장"""
        pass

    @abstractmethod
    async def save_batch(self, results: List[TechnicalResult]) -> None:
        """분석 결과 일괄 저장"""
        pass


class AnalysisNotifierPort(ABC):
    """분석 알림 포트"""

    @abstractmethod
    async def notify_analysis_start(self, analysis_type: str, thread_ts: Optional[str]) -> None:
        """분석 시작 알림"""
        pass

    @abstractmethod
    async def notify_analysis_complete(
        self,
        analysis_type: str,
        total_analyzed: int,
        recommended_count: int,
        thread_ts: Optional[str]
    ) -> None:
        """분석 완료 알림"""
        pass

    @abstractmethod
    async def notify_analysis_error(
        self,
        analysis_type: str,
        error: str,
        thread_ts: Optional[str]
    ) -> None:
        """분석 오류 알림"""
        pass
