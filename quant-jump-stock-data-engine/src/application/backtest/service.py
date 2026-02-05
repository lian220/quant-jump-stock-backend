"""
Backtest Application Service

Kafka 핸들러에서 호출되는 백테스트 서비스.
BacktestEngine을 래핑하여 비동기 인터페이스를 제공합니다.

SCRUM-186: Kafka Consumer + PostgreSQL 결과 저장
"""

import logging
from datetime import date, datetime
from decimal import Decimal
from typing import List, Optional

from .engine import BacktestEngine, BacktestConfig
from .data_loader import DataLoader, YFinanceDataLoader
from .result import BacktestResult
from domain.strategy.models import StrategyDefinition

logger = logging.getLogger(__name__)


class BacktestApplicationService:
    """
    백테스트 애플리케이션 서비스

    Kafka 핸들러에서 호출되며, 전략을 로드하고
    백테스트를 실행합니다.
    """

    def __init__(
        self,
        strategy_repository,  # StrategyRepositoryPort
        data_loader: Optional[DataLoader] = None
    ):
        """
        Args:
            strategy_repository: 전략 저장소 (PostgreSQL 또는 MongoDB)
            data_loader: 시장 데이터 로더 (기본값: YFinanceDataLoader)
        """
        self._strategy_repository = strategy_repository
        self._data_loader = data_loader or YFinanceDataLoader()

    async def run_backtest(
        self,
        strategy_id: int,
        tickers: List[str],
        start_date: str,
        end_date: str,
        initial_capital: float = 10000000.0,
        commission_rate: float = 0.00015,
        slippage_rate: float = 0.0001
    ) -> BacktestResult:
        """
        백테스트 실행

        Args:
            strategy_id: 전략 ID (DB PK)
            tickers: 거래 대상 종목 코드 목록
            start_date: 시작일 (YYYY-MM-DD)
            end_date: 종료일 (YYYY-MM-DD)
            initial_capital: 초기 자본금
            commission_rate: 수수료율
            slippage_rate: 슬리피지율

        Returns:
            BacktestResult: 백테스트 결과

        Raises:
            ValueError: 전략을 찾을 수 없는 경우
            Exception: 백테스트 실행 중 오류
        """
        logger.info(
            f"Starting backtest: strategy_id={strategy_id}, "
            f"tickers={tickers}, period={start_date}~{end_date}"
        )

        # 1. 전략 로드
        strategy = await self._load_strategy(strategy_id)
        if not strategy:
            raise ValueError(f"Strategy not found: {strategy_id}")

        # 2. 백테스트 설정
        config = BacktestConfig(
            start_date=self._parse_date(start_date),
            end_date=self._parse_date(end_date),
            initial_capital=Decimal(str(initial_capital)),
            tickers=tickers,
            commission_rate=Decimal(str(commission_rate)),
            slippage_rate=Decimal(str(slippage_rate))
        )

        # 3. 엔진 생성 및 실행
        engine = BacktestEngine(
            data_loader=self._data_loader,
            config=config
        )

        result = engine.run(strategy)

        # 4. strategy_id를 DB PK로 설정
        result.strategy_id = strategy_id

        logger.info(
            f"Backtest completed: strategy={strategy_id}, "
            f"trades={result.total_trades}, "
            f"return={result.total_return:.2%}"
        )

        return result

    async def _load_strategy(self, strategy_id: int) -> Optional[StrategyDefinition]:
        """
        전략 로드

        DB에서 strategy_id(숫자)로 조회하여 StrategyDefinition으로 변환
        """
        try:
            # 1. DB에서 전략 조회 (strategy_id 필드로 검색)
            strategy = await self._strategy_repository.find_by_db_id(strategy_id)
            if strategy:
                return strategy

            # 2. 대안: strategy_id가 문자열 ID인 경우
            # 일부 전략은 strategy_id 문자열로 저장될 수 있음
            strategy = await self._strategy_repository.find_by_id(str(strategy_id))
            return strategy

        except Exception as e:
            logger.error(f"Failed to load strategy {strategy_id}: {e}")
            return None

    def _parse_date(self, date_str: str) -> date:
        """날짜 문자열 파싱"""
        return datetime.strptime(date_str, "%Y-%m-%d").date()
