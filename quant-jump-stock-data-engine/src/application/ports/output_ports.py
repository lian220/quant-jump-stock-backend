"""
Output Ports (Driven Ports)

외부 시스템과의 통신을 위한 인터페이스 정의.
실제 구현은 adapter/output/ 레이어에서 담당.
"""

from abc import ABC, abstractmethod
from typing import List, Optional, Dict, Any
from datetime import datetime
from dataclasses import dataclass

from domain.strategy.models import StrategyDefinition, SignalType


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
