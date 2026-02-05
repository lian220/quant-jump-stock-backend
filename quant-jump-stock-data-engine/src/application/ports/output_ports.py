"""
Output Ports (Driven Ports)

외부 시스템과의 통신을 위한 인터페이스 정의.
실제 구현은 adapter/output/ 레이어에서 담당.
"""

from abc import ABC, abstractmethod
from typing import List, Optional, Dict, Any
from datetime import datetime
from dataclasses import dataclass

import pandas as pd

from domain.strategy.models import StrategyDefinition, SignalType


@dataclass
class PriceData:
    """시세 데이터 DTO"""
    symbol: str
    timestamp: datetime
    open: float
    high: float
    low: float
    close: float
    volume: int


@dataclass
class IndicatorData:
    """기술적 지표 데이터 DTO"""
    symbol: str
    timestamp: datetime
    indicators: Dict[str, float]  # e.g., {"sma_20": 150.5, "rsi_14": 65.2}


@dataclass
class TradingSignal:
    """생성된 매매 신호 DTO"""
    strategy_id: str
    symbol: str
    signal_type: SignalType
    weight: float
    timestamp: datetime
    rule_name: str
    description: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = None


class StrategyRepositoryPort(ABC):
    """
    전략 저장소 포트

    전략 정의를 로드/저장하는 인터페이스.
    구현체: PostgreSQL, MongoDB, File System 등
    """

    @abstractmethod
    async def find_by_id(self, strategy_id: str) -> Optional[StrategyDefinition]:
        """전략 ID로 조회"""
        pass

    @abstractmethod
    async def find_all_active(self) -> List[StrategyDefinition]:
        """활성화된 모든 전략 조회"""
        pass

    @abstractmethod
    async def find_by_tags(self, tags: List[str]) -> List[StrategyDefinition]:
        """태그로 전략 검색"""
        pass

    @abstractmethod
    async def save(self, strategy: StrategyDefinition) -> None:
        """전략 저장"""
        pass

    @abstractmethod
    async def update(self, strategy: StrategyDefinition) -> None:
        """전략 업데이트"""
        pass


class MarketDataPort(ABC):
    """
    시장 데이터 포트

    주가 및 기술적 지표 데이터를 가져오는 인터페이스.
    구현체: MongoDB, KIS API, 외부 데이터 제공자 등
    """

    @abstractmethod
    async def get_latest_price(self, symbol: str) -> Optional[PriceData]:
        """최신 시세 조회"""
        pass

    @abstractmethod
    async def get_price_history(
        self,
        symbol: str,
        start_date: datetime,
        end_date: Optional[datetime] = None,
        limit: int = 200
    ) -> pd.DataFrame:
        """
        과거 시세 조회

        Returns:
            DataFrame with columns: [open, high, low, close, volume]
            Index: DatetimeIndex
        """
        pass

    @abstractmethod
    async def get_indicators(
        self,
        symbol: str,
        indicator_names: List[str]
    ) -> Optional[IndicatorData]:
        """
        기술적 지표 조회

        Args:
            symbol: 종목 코드
            indicator_names: 필요한 지표 목록 (e.g., ["sma_20", "rsi_14"])
        """
        pass

    @abstractmethod
    async def get_multiple_symbols_latest(
        self,
        symbols: List[str]
    ) -> Dict[str, PriceData]:
        """여러 종목의 최신 시세 일괄 조회"""
        pass


class SignalOutputPort(ABC):
    """
    신호 출력 포트

    생성된 매매 신호를 저장/전송하는 인터페이스.
    구현체: Kafka Producer, MongoDB, REST API 등
    """

    @abstractmethod
    async def publish(self, signal: TradingSignal) -> None:
        """단일 신호 발행"""
        pass

    @abstractmethod
    async def publish_batch(self, signals: List[TradingSignal]) -> None:
        """다수 신호 일괄 발행"""
        pass

    @abstractmethod
    async def save_to_history(self, signal: TradingSignal) -> None:
        """신호 이력 저장"""
        pass


class NotificationPort(ABC):
    """
    알림 포트

    사용자/운영자에게 알림을 전송하는 인터페이스.
    구현체: Slack, Email, Push Notification 등
    """

    @abstractmethod
    async def send_signal_alert(
        self,
        signal: TradingSignal,
        channel: Optional[str] = None
    ) -> None:
        """매매 신호 알림 전송"""
        pass

    @abstractmethod
    async def send_error_alert(
        self,
        error_type: str,
        message: str,
        details: Optional[Dict[str, Any]] = None
    ) -> None:
        """에러 알림 전송"""
        pass

    @abstractmethod
    async def send_daily_summary(
        self,
        date: datetime,
        signals_count: int,
        strategies_executed: int,
        errors_count: int
    ) -> None:
        """일일 요약 리포트 전송"""
        pass
