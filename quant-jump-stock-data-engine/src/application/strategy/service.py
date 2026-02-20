"""
Strategy Service

전략 실행 유스케이스 구현.
포트와 어댑터를 연결하고 비즈니스 로직을 조정.
"""

import logging
from typing import List, Optional, Dict
from datetime import datetime

from domain.strategy.models import StrategyDefinition, SignalType
from domain.common.exceptions import (
    StrategyNotFoundError,
    StrategyExecutionError,
)
from application.ports.input_ports import (
    StrategyExecutionUseCase,
    SignalGenerationUseCase,
    ExecutionRequest,
    ExecutionResult,
    BatchExecutionResult,
)
from application.ports.output_ports import (
    StrategyRepositoryPort,
    SignalOutputPort,
    NotificationPort,
    TradingSignal,
)
from application.strategy.executor import StrategyExecutor, ExecutionTask

logger = logging.getLogger(__name__)


class StrategyService(StrategyExecutionUseCase, SignalGenerationUseCase):
    """
    전략 서비스

    전략 실행 및 신호 생성 유스케이스를 구현.
    포트를 통해 외부 시스템과 통신.
    """

    def __init__(
        self,
        strategy_repository: StrategyRepositoryPort,
        signal_output: SignalOutputPort,
        notification: Optional[NotificationPort] = None,
        max_workers: int = 4,
        min_signal_weight: float = 0.5
    ):
        """
        Args:
            strategy_repository: 전략 저장소 포트
            signal_output: 신호 출력 포트
            notification: 알림 포트 (선택)
            max_workers: 병렬 실행 워커 수
            min_signal_weight: 최소 신호 가중치 필터
        """
        self.strategy_repository = strategy_repository
        self.signal_output = signal_output
        self.notification = notification
        self.executor = StrategyExecutor(max_workers=max_workers)
        self.min_signal_weight = min_signal_weight

    async def execute_single(
        self,
        strategy_id: str,
        symbol: str
    ) -> ExecutionResult:
        """단일 전략-종목 실행"""
        # 전략 로드
        strategy = await self.strategy_repository.find_by_id(strategy_id)
        if strategy is None:
            raise StrategyNotFoundError(f"Strategy not found: {strategy_id}")

        # 전략 실행
        result = await self.executor.execute_single(
            strategy=strategy,
            symbol=symbol,
            price_data=None
        )

        # 유효한 신호면 발행
        if result.success and result.signal_type is not None:
            await self._publish_signal(result)

        return result

    async def execute_batch(
        self,
        request: ExecutionRequest
    ) -> BatchExecutionResult:
        """다수 전략-종목 일괄 실행"""
        # 전략 목록 로드
        if request.strategy_ids:
            strategies = []
            for sid in request.strategy_ids:
                strategy = await self.strategy_repository.find_by_id(sid)
                if strategy:
                    strategies.append(strategy)
        else:
            strategies = await self.strategy_repository.find_all_active()

        if not strategies:
            return BatchExecutionResult(
                total_strategies=0,
                total_symbols=len(request.symbols),
                successful=0,
                failed=0,
                signals_generated=0,
                total_execution_time_ms=0.0,
                results=[],
                errors=["No strategies found"]
            )

        # 실행 태스크 생성
        tasks: List[ExecutionTask] = []
        for strategy in strategies:
            for symbol in request.symbols:
                tasks.append(ExecutionTask(
                    strategy=strategy,
                    symbol=symbol,
                    price_data=None
                ))

        if not tasks:
            return BatchExecutionResult(
                total_strategies=len(strategies),
                total_symbols=len(request.symbols),
                successful=0,
                failed=0,
                signals_generated=0,
                total_execution_time_ms=0.0,
                results=[],
                errors=["No valid price data available"]
            )

        # 일괄 실행
        batch_result = await self.executor.execute_batch(tasks)

        # 유효한 신호 발행
        valid_signals = [
            r for r in batch_result.results
            if r.success and r.signal_type is not None
            and (r.weight or 0) >= self.min_signal_weight
        ]

        for result in valid_signals:
            await self._publish_signal(result)

        # 에러 알림 (있는 경우)
        if batch_result.errors and self.notification:
            await self.notification.send_error_alert(
                error_type="BatchExecutionErrors",
                message=f"{len(batch_result.errors)} errors occurred",
                details={"errors": batch_result.errors[:10]}  # 최대 10개
            )

        return batch_result

    async def execute_all_active(
        self,
        symbols: List[str]
    ) -> BatchExecutionResult:
        """모든 활성 전략을 주어진 종목들에 대해 실행"""
        request = ExecutionRequest(
            symbols=symbols,
            strategy_ids=None,  # 모든 활성 전략
            parallel=True
        )
        return await self.execute_batch(request)

    async def filter_signals(
        self,
        signals: List[ExecutionResult],
        min_weight: float = 0.5
    ) -> List[ExecutionResult]:
        """신호 필터링 (weight 기준)"""
        return [
            s for s in signals
            if s.success and s.signal_type is not None
            and (s.weight or 0) >= min_weight
        ]

    async def aggregate_signals(
        self,
        signals: List[ExecutionResult],
        symbol: str
    ) -> Optional[ExecutionResult]:
        """
        동일 종목에 대한 신호 집계

        매수/매도 신호의 가중치를 합산하여 최종 신호 결정.
        """
        symbol_signals = [
            s for s in signals
            if s.symbol == symbol and s.success and s.signal_type is not None
        ]

        if not symbol_signals:
            return None

        # 신호별 가중치 합산
        buy_weight = sum(
            s.weight or 0 for s in symbol_signals
            if s.signal_type == SignalType.BUY
        )
        sell_weight = sum(
            s.weight or 0 for s in symbol_signals
            if s.signal_type == SignalType.SELL
        )

        # 더 강한 신호 선택
        if buy_weight > sell_weight and buy_weight > 0:
            final_signal_type = SignalType.BUY
            final_weight = buy_weight / len([
                s for s in symbol_signals if s.signal_type == SignalType.BUY
            ])
        elif sell_weight > buy_weight and sell_weight > 0:
            final_signal_type = SignalType.SELL
            final_weight = sell_weight / len([
                s for s in symbol_signals if s.signal_type == SignalType.SELL
            ])
        else:
            # 신호 상충 → HOLD
            final_signal_type = SignalType.HOLD
            final_weight = 0.0

        # 가장 높은 가중치 신호의 전략 ID 사용
        top_signal = max(symbol_signals, key=lambda s: s.weight or 0)

        return ExecutionResult(
            strategy_id=f"aggregated_{top_signal.strategy_id}",
            symbol=symbol,
            success=True,
            signal_type=final_signal_type,
            weight=final_weight,
            rule_name="aggregated",
            metadata={
                "buy_weight": buy_weight,
                "sell_weight": sell_weight,
                "contributing_strategies": [s.strategy_id for s in symbol_signals]
            }
        )

    async def validate_signal(
        self,
        signal: ExecutionResult
    ) -> bool:
        """신호 유효성 검증"""
        if not signal.success:
            return False

        if signal.signal_type is None:
            return False

        if signal.weight is None or signal.weight < self.min_signal_weight:
            return False

        # 추가 검증 로직 (예: 포지션 제한, 리스크 관리 등)
        # 필요시 여기에 추가

        return True

    async def _publish_signal(self, result: ExecutionResult) -> None:
        """신호 발행 헬퍼"""
        if result.signal_type is None:
            return

        signal = TradingSignal(
            strategy_id=result.strategy_id,
            symbol=result.symbol,
            signal_type=result.signal_type,
            weight=result.weight or 0.0,
            timestamp=datetime.now(),
            rule_name=result.rule_name or "unknown",
            description=result.metadata.get("description") if result.metadata else None,
            metadata=result.metadata
        )

        try:
            await self.signal_output.publish(signal)
            await self.signal_output.save_to_history(signal)

            logger.debug(
                f"Signal published: {signal.strategy_id} → {signal.symbol} "
                f"({signal.signal_type.value}, weight={signal.weight:.2f})"
            )

        except Exception as e:
            logger.error(f"Failed to publish signal: {e}")
            if self.notification:
                await self.notification.send_error_alert(
                    error_type="SignalPublishError",
                    message=str(e),
                    details={
                        "strategy_id": result.strategy_id,
                        "symbol": result.symbol
                    }
                )

    def shutdown(self):
        """서비스 종료 시 리소스 정리"""
        self.executor.shutdown()
