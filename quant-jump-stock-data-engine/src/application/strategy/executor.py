"""
Strategy Executor

ThreadPoolExecutor를 사용한 병렬 전략 실행기.
15+ 전략을 동시에 실행하여 성능 최적화.
"""

import asyncio
import logging
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from typing import List, Optional, Dict, Callable, Any
from functools import partial

import pandas as pd

from domain.strategy.models import StrategyDefinition, SignalType
from domain.strategy.interpreter import StrategyInterpreter
from domain.common.exceptions import (
    StrategyExecutionError,
    InvalidStrategyError,
    InsufficientDataError,
)
from application.ports.input_ports import (
    ExecutionResult,
    BatchExecutionResult,
    ExecutionRequest,
)

logger = logging.getLogger(__name__)


@dataclass
class ExecutionTask:
    """실행 태스크 정의"""
    strategy: StrategyDefinition
    symbol: str
    price_data: Optional[pd.DataFrame]


class StrategyExecutor:
    """
    병렬 전략 실행기

    ThreadPoolExecutor를 사용하여 다수의 전략을 병렬로 실행.
    각 전략은 독립적으로 실행되며, 실패해도 다른 전략에 영향 없음.
    """

    def __init__(
        self,
        max_workers: int = 4,
        timeout_seconds: float = 30.0
    ):
        """
        Args:
            max_workers: 최대 동시 실행 워커 수 (기본 4)
            timeout_seconds: 단일 전략 실행 타임아웃 (초)
        """
        self.max_workers = max_workers
        self.timeout_seconds = timeout_seconds
        self.interpreter = StrategyInterpreter()
        self._executor: Optional[ThreadPoolExecutor] = None

    def _get_executor(self) -> ThreadPoolExecutor:
        """Lazy initialization of ThreadPoolExecutor"""
        if self._executor is None:
            self._executor = ThreadPoolExecutor(
                max_workers=self.max_workers,
                thread_name_prefix="strategy_exec_"
            )
        return self._executor

    def shutdown(self):
        """Executor 종료"""
        if self._executor:
            self._executor.shutdown(wait=True)
            self._executor = None

    def execute_single_sync(
        self,
        strategy: StrategyDefinition,
        symbol: str,
        price_data: Optional[pd.DataFrame]
    ) -> ExecutionResult:
        """
        단일 전략 동기 실행 (ThreadPool 내부에서 호출)

        Args:
            strategy: 전략 정의
            symbol: 종목 코드
            price_data: 가격 데이터 (DataFrame)

        Returns:
            ExecutionResult
        """
        start_time = time.perf_counter()

        try:
            # 데이터 검증
            if price_data is None or price_data.empty:
                raise InsufficientDataError(
                    f"No price data for symbol: {symbol}"
                )

            if len(price_data) < 50:  # 최소 50개 데이터 필요
                raise InsufficientDataError(
                    f"Insufficient data for {symbol}: {len(price_data)} rows (min 50)"
                )

            # 전략 실행
            result = self.interpreter.execute(strategy, price_data)

            execution_time = (time.perf_counter() - start_time) * 1000

            # 신호 추출
            signals = result.get("signals", [])

            if not signals:
                # 신호 없음
                return ExecutionResult(
                    strategy_id=strategy.strategy_id,
                    symbol=symbol,
                    success=True,
                    signal_type=None,
                    weight=None,
                    rule_name=None,
                    execution_time_ms=execution_time,
                    metadata={"message": "No signal generated"}
                )

            # 첫 번째 신호 사용 (가장 강한 신호)
            top_signal = signals[0]
            signal_type_str = top_signal.get("type", "").lower()

            if signal_type_str == "buy":
                signal_type = SignalType.BUY
            elif signal_type_str == "sell":
                signal_type = SignalType.SELL
            else:
                signal_type = SignalType.HOLD

            return ExecutionResult(
                strategy_id=strategy.strategy_id,
                symbol=symbol,
                success=True,
                signal_type=signal_type,
                weight=top_signal.get("confidence", 1.0),
                rule_name=top_signal.get("rule"),
                execution_time_ms=execution_time,
                metadata={
                    "analysis_date": result.get("analysis_date"),
                    "conditions_met": top_signal.get("conditions_met"),
                    "conditions_total": top_signal.get("conditions_total"),
                    "all_signals": signals
                }
            )

        except InsufficientDataError as e:
            execution_time = (time.perf_counter() - start_time) * 1000
            logger.warning(f"Insufficient data for {symbol}: {e}")
            return ExecutionResult(
                strategy_id=strategy.strategy_id,
                symbol=symbol,
                success=False,
                error_message=str(e),
                execution_time_ms=execution_time
            )

        except InvalidStrategyError as e:
            execution_time = (time.perf_counter() - start_time) * 1000
            logger.error(f"Invalid strategy {strategy.strategy_id}: {e}")
            return ExecutionResult(
                strategy_id=strategy.strategy_id,
                symbol=symbol,
                success=False,
                error_message=f"Invalid strategy: {e}",
                execution_time_ms=execution_time
            )

        except Exception as e:
            execution_time = (time.perf_counter() - start_time) * 1000
            logger.exception(
                f"Error executing {strategy.strategy_id} for {symbol}"
            )
            return ExecutionResult(
                strategy_id=strategy.strategy_id,
                symbol=symbol,
                success=False,
                error_message=str(e),
                execution_time_ms=execution_time
            )

    def execute_batch_sync(
        self,
        tasks: List[ExecutionTask]
    ) -> BatchExecutionResult:
        """
        다수 태스크 병렬 실행 (동기)

        Args:
            tasks: 실행할 태스크 목록

        Returns:
            BatchExecutionResult
        """
        start_time = time.perf_counter()
        results: List[ExecutionResult] = []
        errors: List[str] = []

        executor = self._get_executor()

        # Submit all tasks
        futures = {
            executor.submit(
                self.execute_single_sync,
                task.strategy,
                task.symbol,
                task.price_data
            ): task
            for task in tasks
        }

        # Collect results with timeout
        for future in as_completed(futures, timeout=self.timeout_seconds):
            task = futures[future]
            try:
                result = future.result()
                results.append(result)
                if not result.success:
                    errors.append(
                        f"{task.strategy.strategy_id}:{task.symbol} - {result.error_message}"
                    )
            except Exception as e:
                error_msg = f"{task.strategy.strategy_id}:{task.symbol} - {str(e)}"
                errors.append(error_msg)
                logger.exception(f"Task failed: {error_msg}")
                results.append(ExecutionResult(
                    strategy_id=task.strategy.strategy_id,
                    symbol=task.symbol,
                    success=False,
                    error_message=str(e),
                    execution_time_ms=0.0
                ))

        total_time = (time.perf_counter() - start_time) * 1000

        # Calculate statistics
        successful = sum(1 for r in results if r.success)
        signals_generated = sum(
            1 for r in results if r.success and r.signal_type is not None
        )

        # Get unique counts
        strategy_ids = set(t.strategy.strategy_id for t in tasks)
        symbols = set(t.symbol for t in tasks)

        return BatchExecutionResult(
            total_strategies=len(strategy_ids),
            total_symbols=len(symbols),
            successful=successful,
            failed=len(results) - successful,
            signals_generated=signals_generated,
            total_execution_time_ms=total_time,
            results=results,
            errors=errors
        )

    async def execute_single(
        self,
        strategy: StrategyDefinition,
        symbol: str,
        price_data: pd.DataFrame
    ) -> ExecutionResult:
        """
        단일 전략 비동기 실행

        Args:
            strategy: 전략 정의
            symbol: 종목 코드
            price_data: 가격 데이터

        Returns:
            ExecutionResult
        """
        loop = asyncio.get_event_loop()
        executor = self._get_executor()

        return await loop.run_in_executor(
            executor,
            partial(
                self.execute_single_sync,
                strategy,
                symbol,
                price_data
            )
        )

    async def execute_batch(
        self,
        tasks: List[ExecutionTask]
    ) -> BatchExecutionResult:
        """
        다수 태스크 비동기 병렬 실행

        Args:
            tasks: 실행할 태스크 목록

        Returns:
            BatchExecutionResult
        """
        loop = asyncio.get_event_loop()
        executor = self._get_executor()

        return await loop.run_in_executor(
            executor,
            partial(self.execute_batch_sync, tasks)
        )

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.shutdown()
        return False


# Convenience function for creating executor with context manager
def create_executor(
    max_workers: int = 4,
    timeout_seconds: float = 30.0
) -> StrategyExecutor:
    """
    Executor 팩토리 함수

    Usage:
        with create_executor(max_workers=8) as executor:
            results = executor.execute_batch_sync(tasks)
    """
    return StrategyExecutor(
        max_workers=max_workers,
        timeout_seconds=timeout_seconds
    )
