"""
Input Ports (Driving Ports / Use Cases)

애플리케이션의 진입점을 정의하는 인터페이스.
외부 어댑터(REST API, Scheduler, CLI 등)에서 호출.
"""

from abc import ABC, abstractmethod
from typing import List, Optional, Dict, Any
from dataclasses import dataclass
from datetime import datetime

from domain.strategy.models import SignalType


@dataclass
class ExecutionRequest:
    """전략 실행 요청 DTO"""
    symbols: List[str]
    strategy_ids: Optional[List[str]] = None  # None이면 모든 활성 전략
    parallel: bool = True
    max_workers: int = 4


@dataclass
class ExecutionResult:
    """전략 실행 결과 DTO"""
    strategy_id: str
    symbol: str
    success: bool
    signal_type: Optional[SignalType] = None
    weight: Optional[float] = None
    rule_name: Optional[str] = None
    error_message: Optional[str] = None
    execution_time_ms: float = 0.0
    metadata: Optional[Dict[str, Any]] = None


@dataclass
class BatchExecutionResult:
    """일괄 실행 결과 DTO"""
    total_strategies: int
    total_symbols: int
    successful: int
    failed: int
    signals_generated: int
    total_execution_time_ms: float
    results: List[ExecutionResult]
    errors: List[str]


class StrategyExecutionUseCase(ABC):
    """
    전략 실행 유스케이스

    전략을 실행하고 신호를 생성하는 핵심 비즈니스 로직.
    """

    @abstractmethod
    async def execute_single(
        self,
        strategy_id: str,
        symbol: str
    ) -> ExecutionResult:
        """
        단일 전략-종목 조합 실행

        Args:
            strategy_id: 실행할 전략 ID
            symbol: 분석할 종목 코드

        Returns:
            ExecutionResult: 실행 결과
        """
        pass

    @abstractmethod
    async def execute_batch(
        self,
        request: ExecutionRequest
    ) -> BatchExecutionResult:
        """
        다수 전략-종목 일괄 실행

        Args:
            request: 실행 요청 (종목 목록, 전략 목록, 병렬 설정)

        Returns:
            BatchExecutionResult: 일괄 실행 결과
        """
        pass

    @abstractmethod
    async def execute_all_active(
        self,
        symbols: List[str]
    ) -> BatchExecutionResult:
        """
        모든 활성 전략을 주어진 종목들에 대해 실행

        Args:
            symbols: 분석할 종목 목록

        Returns:
            BatchExecutionResult: 일괄 실행 결과
        """
        pass


class SignalGenerationUseCase(ABC):
    """
    신호 생성 유스케이스

    생성된 신호를 처리하고 필터링하는 로직.
    """

    @abstractmethod
    async def filter_signals(
        self,
        signals: List[ExecutionResult],
        min_weight: float = 0.5
    ) -> List[ExecutionResult]:
        """
        신호 필터링 (weight 기준)

        Args:
            signals: 필터링할 신호 목록
            min_weight: 최소 가중치 (기본 0.5)

        Returns:
            필터링된 신호 목록
        """
        pass

    @abstractmethod
    async def aggregate_signals(
        self,
        signals: List[ExecutionResult],
        symbol: str
    ) -> Optional[ExecutionResult]:
        """
        동일 종목에 대한 신호 집계

        여러 전략에서 동일 종목에 대해 생성된 신호를
        하나의 최종 신호로 합산.

        Args:
            signals: 집계할 신호 목록
            symbol: 대상 종목

        Returns:
            집계된 최종 신호 (신호가 없으면 None)
        """
        pass

    @abstractmethod
    async def validate_signal(
        self,
        signal: ExecutionResult
    ) -> bool:
        """
        신호 유효성 검증

        리스크 관리 규칙에 따라 신호 유효성 검증.

        Args:
            signal: 검증할 신호

        Returns:
            유효 여부
        """
        pass
