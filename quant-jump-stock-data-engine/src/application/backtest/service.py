"""
Backtest Application Service

Kafka 핸들러에서 호출되는 백테스트 서비스.
BacktestEngine을 래핑하여 비동기 인터페이스를 제공합니다.

SCRUM-186: Kafka Consumer + PostgreSQL 결과 저장
증분 백테스트 지원 추가
"""

import logging
from datetime import date, datetime
from decimal import Decimal
from typing import List, Optional, Dict, Any, Tuple

from .engine import BacktestEngine, BacktestConfig
from .data_loader import DataLoader
from .data_loader_mongo import MongoDataLoader
from .result import BacktestResult, BacktestTrade
from domain.strategy.models import StrategyDefinition

logger = logging.getLogger(__name__)

# 기본 벤치마크 (Core API Benchmark Enum과 동기화)
DEFAULT_BENCHMARK = "SPY"


class IncrementalBacktestResult:
    """증분 백테스트 결과"""
    def __init__(
        self,
        result: BacktestResult,
        is_incremental: bool,
        backtest_id: Optional[int],
        new_trades: List[BacktestTrade],
        checkpoint_data: Dict[str, Any]
    ):
        self.result = result
        self.is_incremental = is_incremental
        self.backtest_id = backtest_id  # 기존 ID (증분 시) 또는 None (새 생성 시)
        self.new_trades = new_trades  # 새로 추가된 거래만
        self.checkpoint_data = checkpoint_data  # 저장할 체크포인트


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
            data_loader: 시장 데이터 로더 (기본값: MongoDataLoader - 4x 빠름)
        """
        self._strategy_repository = strategy_repository
        self._data_loader = data_loader or MongoDataLoader()

    async def run_backtest(
        self,
        strategy_id: int,
        tickers: List[str],
        start_date: str,
        end_date: str,
        initial_capital: float = 10000000.0,
        commission_rate: float = 0.00015,
        slippage_rate: float = 0.0001,
        benchmark: str = DEFAULT_BENCHMARK
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
            slippage_rate=Decimal(str(slippage_rate)),
            benchmark_ticker=benchmark,
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

    async def run_backtest_incremental(
        self,
        strategy_id: int,
        tickers: List[str],
        start_date: str,
        end_date: str,
        initial_capital: float = 10000000.0,
        commission_rate: float = 0.00015,
        slippage_rate: float = 0.0001,
        existing_backtest: Optional[Dict[str, Any]] = None,
        checkpoint: Optional[Dict[str, Any]] = None,
        equity_curve_data: Optional[List[Dict[str, Any]]] = None,
        benchmark: str = DEFAULT_BENCHMARK,
        user_id: Optional[int] = None,
        # SCRUM-330: 리스크 관리 파라미터
        risk_settings: Optional[Dict[str, Any]] = None,
        position_sizing: Optional[Dict[str, Any]] = None,
        trading_costs: Optional[Dict[str, Any]] = None,
    ) -> IncrementalBacktestResult:
        """
        증분 백테스트 실행

        기존 백테스트가 있으면 체크포인트에서 재개,
        없으면 전체 백테스트를 새로 실행합니다.

        Args:
            strategy_id: 전략 ID
            tickers: 종목 코드 목록
            start_date: 시작일
            end_date: 종료일
            initial_capital: 초기 자본금
            commission_rate: 수수료율
            slippage_rate: 슬리피지율
            existing_backtest: 기존 백테스트 정보 (있으면 증분 실행)
            checkpoint: 체크포인트 데이터 (증분 시 필요)
            equity_curve_data: 수익 곡선 데이터 (backtest_results에서 조회, 증분 시 필요)

        Returns:
            IncrementalBacktestResult: 증분 백테스트 결과
        """
        logger.info(
            f"Starting {'incremental' if existing_backtest else 'full'} backtest: "
            f"strategy_id={strategy_id}, period={start_date}~{end_date}"
        )

        # 1. 전략 로드
        strategy = await self._load_strategy(strategy_id)
        if not strategy:
            raise ValueError(f"Strategy not found: {strategy_id}")

        # 2. 백테스트 설정
        # SCRUM-330: 리스크 파라미터 적용
        position_sizing_method = "fixed_percentage"
        max_position_pct = Decimal("0.1")
        if position_sizing:
            position_sizing_method = position_sizing.get("method", "fixed_percentage")
            max_pct = position_sizing.get("maxPositionPct")
            if max_pct is not None:
                max_position_pct = Decimal(str(max_pct)) / Decimal("100")

        tax_rate = Decimal("0.0023")
        slippage_model = "fixed"
        if trading_costs:
            tax_val = trading_costs.get("tax")
            if tax_val is not None:
                tax_rate = Decimal(str(tax_val)) / Decimal("100")
            slippage_model = trading_costs.get("slippageModel", "fixed")

        config = BacktestConfig(
            start_date=self._parse_date(start_date),
            end_date=self._parse_date(end_date),
            initial_capital=Decimal(str(initial_capital)),
            tickers=tickers,
            commission_rate=Decimal(str(commission_rate)),
            slippage_rate=Decimal(str(slippage_rate)),
            benchmark_ticker=benchmark,
            risk_settings=risk_settings,
            position_sizing_method=position_sizing_method,
            position_size_pct=max_position_pct,
            tax_rate=tax_rate,
            slippage_model=slippage_model,
        )

        # 3. 엔진 생성
        engine = BacktestEngine(
            data_loader=self._data_loader,
            config=config
        )

        # 5. 증분 실행 또는 전체 실행
        if existing_backtest and checkpoint:
            # 증분 실행 (equity_curve는 backtest_results에서 조회한 데이터 사용)
            result, new_trades = await self._run_incremental(
                engine, strategy, checkpoint, equity_curve_data
            )
            is_incremental = True
            backtest_id = existing_backtest.get("id")
        else:
            # 전체 실행
            result = engine.run(strategy)
            new_trades = result.trades  # 모든 거래가 "새" 거래
            is_incremental = False
            backtest_id = None

        # 5. 체크포인트 생성 (equity_curve 미포함)
        checkpoint_data = engine.create_checkpoint()

        # 6. strategy_id, user_id 설정
        result.strategy_id = strategy_id
        result.user_id = user_id

        logger.info(
            f"{'Incremental' if is_incremental else 'Full'} backtest completed: "
            f"strategy={strategy_id}, trades={result.total_trades}, "
            f"return={result.total_return:.2%}"
        )

        return IncrementalBacktestResult(
            result=result,
            is_incremental=is_incremental,
            backtest_id=backtest_id,
            new_trades=new_trades,
            checkpoint_data=checkpoint_data
        )

    async def _run_incremental(
        self,
        engine: BacktestEngine,
        strategy: StrategyDefinition,
        checkpoint: Any,  # BacktestCheckpoint from repository
        equity_curve_data: Optional[List[Dict[str, Any]]] = None
    ) -> Tuple[BacktestResult, List[BacktestTrade]]:
        """
        체크포인트에서 증분 실행

        Args:
            engine: 백테스트 엔진
            strategy: 전략 정의
            checkpoint: BacktestCheckpoint 객체 (포트폴리오 상태)
            equity_curve_data: 수익 곡선 데이터 (backtest_results에서 조회)

        Returns:
            (백테스트 결과, 새 거래 목록)
        """
        # 체크포인트에서 상태 복원 (equity_curve는 별도 파라미터)
        checkpoint_data = {
            "checkpoint_date": checkpoint.checkpoint_date,
            "cash": checkpoint.cash,
            "high_watermark": checkpoint.high_watermark,
            "positions": checkpoint.positions,
        }

        engine.resume_from_checkpoint(
            checkpoint_data,
            strategy,
            equity_curve_data=equity_curve_data
        )

        # 체크포인트 시점의 거래 수 기록
        trade_count_before = checkpoint.trade_count

        # 증분 실행
        result = engine.run_incremental(
            strategy,
            resume_from_date=checkpoint.checkpoint_date
        )

        # 새 거래만 추출
        new_trades = engine.get_new_trades_since(trade_count_before)

        return result, new_trades
