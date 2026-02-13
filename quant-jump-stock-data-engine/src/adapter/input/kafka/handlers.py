"""
Kafka Message Handlers

토픽별 메시지 처리 핸들러.
각 핸들러는 Application 서비스를 호출하여 비즈니스 로직 실행.
"""

import logging
import time
from abc import ABC, abstractmethod
from decimal import Decimal
from typing import Optional, Protocol
from datetime import datetime, date
from pytz import timezone

from adapter.output.postgresql.backtest_repository import BacktestCheckpoint
from application.backtest.service import DEFAULT_BENCHMARK
from .consumer import KafkaMessage

KST = timezone('Asia/Seoul')
logger = logging.getLogger(__name__)


class MessageHandler(ABC):
    """메시지 핸들러 추상 클래스"""

    @property
    @abstractmethod
    def topic(self) -> str:
        """처리할 토픽명"""
        pass

    @abstractmethod
    def handle(self, message: KafkaMessage) -> None:
        """메시지 처리"""
        pass

    def _log_start(self, message: KafkaMessage, description: str) -> float:
        """처리 시작 로깅"""
        date_info = self._format_date_range(message.start_date, message.end_date)
        logger.info("=" * 80)
        logger.info(f"{description} Kafka 메시지 수신")
        logger.info(f"Request ID: {message.request_id}")
        logger.info(f"Date Range: {date_info}")
        logger.info(f"Thread TS: {message.thread_ts}")
        logger.info("=" * 80)
        return time.time()

    @staticmethod
    def _format_date_range(start_date: Optional[str], end_date: Optional[str]) -> str:
        if start_date and end_date:
            return f"{start_date} ~ {end_date}"
        elif start_date:
            return f"{start_date} ~ 오늘"
        else:
            return "자동 (마지막 수집일+1 ~ 오늘)"

    def _log_success(self, description: str, start_time: float) -> None:
        """성공 로깅"""
        elapsed = time.time() - start_time
        logger.info(f"✅ {description} 완료 ({elapsed:.2f}초)")

    def _log_error(self, description: str, error: Exception) -> None:
        """에러 로깅"""
        logger.error(f"❌ {description} 실패: {error}")


# ============================================================
# Protocol 정의 (Application 서비스 인터페이스)
# ============================================================

class EconomicDataServiceProtocol(Protocol):
    """경제 데이터 서비스 프로토콜"""
    def collect_economic_data(self, start_date: Optional[str] = None, end_date: Optional[str] = None) -> dict:
        ...


class TechnicalAnalysisServiceProtocol(Protocol):
    """기술적 분석 서비스 프로토콜"""
    def run_technical_analysis(
        self,
        request_id: str,
        thread_ts: Optional[str],
        start_date: Optional[str] = None,
        end_date: Optional[str] = None
    ) -> dict:
        ...


class SentimentAnalysisServiceProtocol(Protocol):
    """감정 분석 서비스 프로토콜"""
    def run_sentiment_analysis(
        self,
        request_id: str,
        thread_ts: Optional[str],
        start_date: Optional[str] = None,
        end_date: Optional[str] = None
    ) -> dict:
        ...


class NotifierProtocol(Protocol):
    """알림 서비스 프로토콜"""
    def notify_start(self, request_id: str, source: str, thread_ts: Optional[str]) -> None:
        ...

    def notify_success(self, request_id: str, summary: dict, thread_ts: Optional[str]) -> None:
        ...

    def notify_error(self, request_id: str, error: str, thread_ts: Optional[str]) -> None:
        ...


class EventPublisherProtocol(Protocol):
    """이벤트 발행 프로토콜"""
    def publish(self, event_type: str, data: dict) -> None:
        ...


# ============================================================
# 핸들러 구현
# ============================================================

class EconomicDataHandler(MessageHandler):
    """경제 데이터 업데이트 핸들러"""

    def __init__(
        self,
        service: EconomicDataServiceProtocol,
        notifier: Optional[NotifierProtocol] = None,
        publisher: Optional[EventPublisherProtocol] = None
    ):
        self.service = service
        self.notifier = notifier
        self.publisher = publisher

    @property
    def topic(self) -> str:
        return "economic.data.update.request"

    def handle(self, message: KafkaMessage) -> None:
        start_time = self._log_start(message, "경제 데이터 업데이트")

        # 수집 시작 알림
        if self.notifier:
            source = message.payload.get("source", "kafka")
            self.notifier.notify_start(message.request_id, source, message.thread_ts)

        try:
            result = self.service.collect_economic_data(
                start_date=message.start_date,
                end_date=message.end_date
            )
            elapsed = time.time() - start_time

            self._log_success("경제 데이터 수집", start_time)

            # 성공 알림
            summary = {
                "start_date": result.get("start_date"),
                "end_date": result.get("end_date"),
                "duration": f"{elapsed:.2f}초",
                "fred_collected": result.get("fred_collected", 0),
                "yahoo_collected": result.get("yahoo_collected", 0),
                "total_indicators": result.get("fred_collected", 0) + result.get("yahoo_collected", 0)
            }

            if self.notifier:
                self.notifier.notify_success(message.request_id, summary, message.thread_ts)

            if self.publisher:
                self.publisher.publish("ECONOMIC_DATA_UPDATED", {
                    "status": "success",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "duration": elapsed
                })

        except Exception as e:
            self._log_error("경제 데이터 수집", e)

            if self.notifier:
                self.notifier.notify_error(message.request_id, str(e), message.thread_ts)

            if self.publisher:
                self.publisher.publish("ECONOMIC_DATA_UPDATE_FAILED", {
                    "status": "failed",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "error": str(e)
                })
            raise


class TechnicalAnalysisHandler(MessageHandler):
    """기술적 분석 핸들러"""

    def __init__(
        self,
        service: TechnicalAnalysisServiceProtocol,
        publisher: Optional[EventPublisherProtocol] = None
    ):
        self.service = service
        self.publisher = publisher

    @property
    def topic(self) -> str:
        return "analysis.technical.request"

    def handle(self, message: KafkaMessage) -> None:
        start_time = self._log_start(message, "기술적 분석 요청")

        try:
            result = self.service.run_technical_analysis(
                message.request_id,
                message.thread_ts,
                start_date=message.start_date,
                end_date=message.end_date
            )

            self._log_success("기술적 분석", start_time)

            # 🆕 MongoDB → PostgreSQL 동기화 (분석 완료 후)
            try:
                from application.recommendation.sync_service import RecommendationSyncService

                # 분석 날짜 결정 (end_date 또는 오늘)
                analysis_date = message.end_date or date.today().isoformat()

                sync_service = RecommendationSyncService()
                sync_result = sync_service.sync_latest_recommendations(analysis_date)

                logger.info(f"🔄 동기화 완료: {sync_result.get('synced_count', 0)}개 종목")
            except Exception as sync_error:
                logger.error(f"⚠️ 동기화 실패 (분석은 성공): {sync_error}")
                # 동기화 실패해도 분석 성공은 유지

            if self.publisher:
                self.publisher.publish("ANALYSIS_TECHNICAL_COMPLETED", {
                    "status": "success",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "duration": time.time() - start_time,
                    "result": result
                })

        except Exception as e:
            self._log_error("기술적 분석", e)

            if self.publisher:
                self.publisher.publish("ANALYSIS_TECHNICAL_FAILED", {
                    "status": "failed",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "error": str(e)
                })
            raise


class SentimentAnalysisHandler(MessageHandler):
    """감정 분석 핸들러"""

    def __init__(
        self,
        service: SentimentAnalysisServiceProtocol,
        publisher: Optional[EventPublisherProtocol] = None
    ):
        self.service = service
        self.publisher = publisher

    @property
    def topic(self) -> str:
        return "analysis.sentiment.request"

    def handle(self, message: KafkaMessage) -> None:
        start_time = self._log_start(message, "뉴스 감정 분석 요청")

        try:
            result = self.service.run_sentiment_analysis(
                message.request_id,
                message.thread_ts,
                start_date=message.start_date,
                end_date=message.end_date
            )

            self._log_success("뉴스 감정 분석", start_time)

            if self.publisher:
                self.publisher.publish("ANALYSIS_SENTIMENT_COMPLETED", {
                    "status": "success",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "duration": time.time() - start_time,
                    "result": result
                })

        except Exception as e:
            self._log_error("뉴스 감정 분석", e)

            if self.publisher:
                self.publisher.publish("ANALYSIS_SENTIMENT_FAILED", {
                    "status": "failed",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "error": str(e)
                })
            raise


class StrategyExecutionHandler(MessageHandler):
    """
    전략 실행 핸들러 (신규)

    새로 추가된 DSL 기반 전략 실행을 처리.
    """

    def __init__(
        self,
        service,  # StrategyService
        publisher: Optional[EventPublisherProtocol] = None
    ):
        self.service = service
        self.publisher = publisher

    @property
    def topic(self) -> str:
        return "strategy.execution.request"

    def handle(self, message: KafkaMessage) -> None:
        start_time = self._log_start(message, "전략 실행 요청")

        try:
            # 메시지에서 종목 목록 추출
            symbols = message.payload.get("symbols", [])
            strategy_ids = message.payload.get("strategyIds")

            if not symbols:
                raise ValueError("symbols 필드가 필요합니다")

            # 비동기 실행을 동기로 래핑 (Kafka 핸들러는 동기)
            import asyncio

            async def _execute():
                from application.ports.input_ports import ExecutionRequest
                request = ExecutionRequest(
                    symbols=symbols,
                    strategy_ids=strategy_ids,
                    parallel=True
                )
                return await self.service.execute_batch(request)

            # 이벤트 루프 생성/재사용
            try:
                loop = asyncio.get_event_loop()
            except RuntimeError:
                loop = asyncio.new_event_loop()
                asyncio.set_event_loop(loop)

            result = loop.run_until_complete(_execute())

            self._log_success("전략 실행", start_time)

            if self.publisher:
                self.publisher.publish("STRATEGY_EXECUTION_COMPLETED", {
                    "status": "success",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "duration": time.time() - start_time,
                    "totalStrategies": result.total_strategies,
                    "totalSymbols": result.total_symbols,
                    "signalsGenerated": result.signals_generated,
                    "successful": result.successful,
                    "failed": result.failed
                })

        except Exception as e:
            self._log_error("전략 실행", e)

            if self.publisher:
                self.publisher.publish("STRATEGY_EXECUTION_FAILED", {
                    "status": "failed",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "error": str(e)
                })
            raise


class BacktestServiceProtocol(Protocol):
    """백테스트 서비스 프로토콜"""
    async def run_backtest(
        self,
        strategy_id: int,
        symbols: list,
        start_date: str,
        end_date: str,
        initial_capital: float,
        commission_rate: float,
        slippage_rate: float,
        benchmark: str = DEFAULT_BENCHMARK
    ) -> object:
        ...

    async def run_backtest_incremental(
        self,
        strategy_id: int,
        tickers: list,
        start_date: str,
        end_date: str,
        initial_capital: float,
        commission_rate: float,
        slippage_rate: float,
        existing_backtest: Optional[dict],
        checkpoint: Optional[object],
        equity_curve_data: Optional[list] = None,
        benchmark: str = DEFAULT_BENCHMARK
    ) -> object:
        """증분 백테스트 실행"""
        ...


class BacktestRepositoryProtocol(Protocol):
    """백테스트 결과 저장소 프로토콜"""
    async def save_result(self, result: object, request_id: Optional[str] = None) -> int:
        ...

    async def find_active_backtest(
        self,
        strategy_id: int,
        tickers: list,
        start_date: str
    ) -> Optional[dict]:
        """기존 활성 백테스트 조회"""
        ...

    async def get_latest_checkpoint(self, backtest_id: int) -> Optional[object]:
        """최신 체크포인트 조회"""
        ...

    async def get_equity_curve_until(
        self,
        backtest_id: int,
        until_date: date
    ) -> list:
        """특정 날짜까지의 equity_curve 조회 (backtest_results에서)"""
        ...

    async def save_checkpoint(self, checkpoint: object) -> int:
        """체크포인트 저장"""
        ...

    async def update_result_incremental(
        self,
        backtest_id: int,
        result: object,
        new_trades: list,
        request_id: Optional[str] = None
    ) -> None:
        """증분 결과 업데이트"""
        ...


class VertexAIPredictionServiceProtocol(Protocol):
    """Vertex AI 예측 서비스 프로토콜"""
    def run_prediction(
        self,
        target_date: Optional[str] = None,
        env_vars: Optional[dict] = None,
        thread_ts: Optional[str] = None
    ) -> object:
        ...


class VertexAIHandler(MessageHandler):
    """
    Vertex AI 예측 실행 핸들러

    Kafka 메시지를 받아 Vertex AI Custom Job을 실행합니다.
    """

    def __init__(
        self,
        service: VertexAIPredictionServiceProtocol,
        publisher: Optional[EventPublisherProtocol] = None
    ):
        self.service = service
        self.publisher = publisher

    @property
    def topic(self) -> str:
        return "vertex.ai.run.request"

    def handle(self, message: KafkaMessage) -> None:
        start_time = self._log_start(message, "Vertex AI 예측 실행 요청")

        try:
            # 메시지에서 파라미터 추출
            env_vars = message.payload.get("envVars", {})
            thread_ts = message.thread_ts
            target_date = message.target_date  # targetDate 추출

            # 예측 실행
            result = self.service.run_prediction(
                target_date=target_date,
                env_vars=env_vars,
                thread_ts=thread_ts
            )

            if result.success:
                self._log_success("Vertex AI 예측 실행", start_time)

                if self.publisher:
                    self.publisher.publish("VERTEX_AI_JOB_SUBMITTED", {
                        "status": "success",
                        "timestamp": datetime.now(KST).isoformat(),
                        "requestId": message.request_id,
                        "jobName": result.job_name,
                        "duration": time.time() - start_time
                    })
            else:
                raise RuntimeError(result.message)

        except Exception as e:
            self._log_error("Vertex AI 예측 실행", e)

            if self.publisher:
                self.publisher.publish("VERTEX_AI_JOB_FAILED", {
                    "status": "failed",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "error": str(e)
                })
            raise


class BacktestRequestHandler(MessageHandler):
    """
    백테스트 실행 요청 핸들러

    SCRUM-186: Kafka로 백테스트 요청을 받아 실행하고
    결과를 PostgreSQL에 저장합니다.
    """

    def __init__(
        self,
        backtest_service: BacktestServiceProtocol,
        backtest_repository: BacktestRepositoryProtocol,
        publisher: Optional[EventPublisherProtocol] = None
    ):
        self.backtest_service = backtest_service
        self.backtest_repository = backtest_repository
        self.publisher = publisher

    @property
    def topic(self) -> str:
        return "quantiq.backtest.request"

    def handle(self, message: KafkaMessage) -> None:
        start_time = self._log_start(message, "백테스트 실행 요청")

        # 페이로드에서 파라미터 추출
        payload = message.payload
        strategy_id = payload.get("strategyId")
        tickers = payload.get("tickers", [])
        start_date = payload.get("startDate")
        end_date = payload.get("endDate")
        initial_capital = payload.get("initialCapital", 10000000.0)
        commission_rate = payload.get("commissionRate", 0.00015)
        slippage_rate = payload.get("slippageRate", 0.0001)
        force_full = payload.get("forceFull", False)  # 강제 전체 실행 옵션
        benchmark = payload.get("benchmark", DEFAULT_BENCHMARK)
        user_id = payload.get("userId")
        if user_id is not None:
            try:
                user_id = int(user_id)
            except (ValueError, TypeError):
                user_id = None

        if not strategy_id:
            self._publish_failure(message, "strategyId is required", start_time)
            raise ValueError("strategyId is required")

        if not tickers:
            self._publish_failure(message, "tickers is required", start_time)
            raise ValueError("tickers is required")

        if not start_date or not end_date:
            self._publish_failure(message, "startDate and endDate are required", start_time)
            raise ValueError("startDate and endDate are required")

        try:
            # [개선] 백테스트 실행 전 데이터 존재 여부 확인
            # MongoDB에서 요청된 종목의 데이터가 있는지 검증
            from application.backtest.data_loader_mongo import MongoDataLoader
            import os
            
            try:
                data_loader = MongoDataLoader(
                    uri=os.environ.get("MONGODB_URI"),
                    database=os.environ.get("MONGODB_DB_NAME", "stock_trading")
                )
                
                # 시작일과 종료일 사이에 데이터가 있는지 확인
                from datetime import datetime
                start_dt = datetime.strptime(start_date, "%Y-%m-%d").date()
                end_dt = datetime.strptime(end_date, "%Y-%m-%d").date()
                
                # 각 종목별로 데이터 존재 여부 확인
                missing_tickers = []
                for ticker in tickers:
                    try:
                        ticker_data = data_loader.load_single(ticker, start_dt, end_dt)
                        if ticker_data.empty:
                            missing_tickers.append(ticker)
                    except Exception:
                        missing_tickers.append(ticker)
                
                if missing_tickers:
                    error_msg = f"다음 종목의 데이터가 MongoDB에 없습니다: {', '.join(missing_tickers)}"
                    logger.error(error_msg)
                    self._publish_failure(message, error_msg, start_time)
                    raise ValueError(error_msg)
                    
                logger.info(f"모든 종목({len(tickers)}개)의 데이터 존재 확인 완료")
                
            except Exception as data_check_error:
                logger.error(f"데이터 존재 여부 확인 중 오류: {data_check_error}")
                # 데이터 체크 실패 시에도 백테스트는 진행 (기존 동작 유지)
                logger.warning("데이터 체크를 건너뛰고 백테스트를 진행합니다")
            
            # 비동기 실행을 동기로 래핑
            import asyncio

            async def _execute():
                existing_backtest = None
                checkpoint = None
                equity_curve_data = None

                # 강제 전체 실행이 아닌 경우, 기존 백테스트 및 체크포인트 조회
                if not force_full:
                    existing_backtest = await self.backtest_repository.find_active_backtest(
                        strategy_id=strategy_id,
                        tickers=tickers,
                        start_date=start_date
                    )

                    if existing_backtest:
                        checkpoint = await self.backtest_repository.get_latest_checkpoint(
                            existing_backtest["id"]
                        )
                        # 체크포인트가 있으면 equity_curve도 조회 (backtest_results에서)
                        if checkpoint:
                            equity_curve_data = await self.backtest_repository.get_equity_curve_until(
                                existing_backtest["id"],
                                checkpoint.checkpoint_date
                            )
                        logger.info(
                            f"기존 백테스트 발견: id={existing_backtest['id']}, "
                            f"checkpoint={'있음' if checkpoint else '없음'}, "
                            f"equity_curve_points={len(equity_curve_data) if equity_curve_data else 0}"
                        )

                # 증분 백테스트 실행 (equity_curve는 backtest_results에서 조회한 데이터 전달)
                incremental_result = await self.backtest_service.run_backtest_incremental(
                    strategy_id=strategy_id,
                    tickers=tickers,
                    start_date=start_date,
                    end_date=end_date,
                    initial_capital=initial_capital,
                    commission_rate=commission_rate,
                    slippage_rate=slippage_rate,
                    existing_backtest=existing_backtest,
                    checkpoint=checkpoint,
                    equity_curve_data=equity_curve_data,
                    benchmark=benchmark,
                    user_id=user_id
                )

                result = incremental_result.result

                # 결과 저장 (증분 vs 전체)
                if incremental_result.is_incremental and incremental_result.backtest_id:
                    # 증분: 기존 결과 업데이트
                    await self.backtest_repository.update_result_incremental(
                        backtest_id=incremental_result.backtest_id,
                        result=result,
                        new_trades=incremental_result.new_trades,
                        request_id=message.request_id
                    )
                    result_id = incremental_result.backtest_id
                    logger.info(f"증분 백테스트 결과 업데이트: id={result_id}")
                else:
                    # 전체: 새 결과 저장 (user_id 포함)
                    result.user_id = user_id
                    result_id = await self.backtest_repository.save_result(
                        result,
                        request_id=message.request_id
                    )
                    logger.info(f"새 백테스트 결과 저장: id={result_id}")

                # 체크포인트 저장 (equity_curve는 backtest_results에 저장되므로 제외)
                checkpoint_data = incremental_result.checkpoint_data
                if not checkpoint_data:
                    logger.warning(f"checkpoint_data가 None입니다. 체크포인트 저장을 건너뜁니다. backtest_id={result_id}")
                    return result, result_id, incremental_result.is_incremental
                checkpoint_date_value = checkpoint_data.get("checkpoint_date")
                if checkpoint_date_value is None:
                    logger.warning(f"checkpoint_date가 None입니다. 체크포인트 저장을 건너뜁니다. backtest_id={result_id}")
                    return result, result_id, incremental_result.is_incremental
                # checkpoint_date가 이미 date 객체인 경우와 문자열인 경우 모두 처리
                if isinstance(checkpoint_date_value, date):
                    checkpoint_date = checkpoint_date_value
                else:
                    checkpoint_date = datetime.strptime(checkpoint_date_value, "%Y-%m-%d").date()

                new_checkpoint = BacktestCheckpoint(
                    backtest_id=result_id,
                    checkpoint_date=checkpoint_date,
                    cash=Decimal(str(checkpoint_data["cash"])),
                    high_watermark=Decimal(str(checkpoint_data["high_watermark"])),
                    positions=checkpoint_data["positions"],
                    trade_count=checkpoint_data["trade_count"]
                )
                await self.backtest_repository.save_checkpoint(new_checkpoint)
                logger.info(f"체크포인트 저장: backtest_id={result_id}, date={checkpoint_date}")

                return result, result_id, incremental_result.is_incremental

            # 이벤트 루프 생성/재사용
            try:
                loop = asyncio.get_event_loop()
            except RuntimeError:
                loop = asyncio.new_event_loop()
                asyncio.set_event_loop(loop)

            result, result_id, is_incremental = loop.run_until_complete(_execute())

            elapsed = time.time() - start_time
            execution_type = "증분" if is_incremental else "전체"
            self._log_success(f"백테스트 실행 ({execution_type})", start_time)

            # 성공 이벤트 발행
            if self.publisher:
                self.publisher.publish("BACKTEST_COMPLETED", {
                    "status": "completed",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "backtestResultId": result_id,
                    "strategyId": strategy_id,
                    "strategyName": result.strategy_name,
                    "userId": user_id,
                    "tickers": tickers,
                    "startDate": start_date,
                    "endDate": end_date,
                    "isIncremental": is_incremental,
                    "initialCapital": float(result.initial_capital),
                    "finalValue": float(result.final_value),
                    "totalReturn": float(result.total_return),
                    "totalReturnPct": float((result.total_return / result.initial_capital) * 100),
                    "cagr": float(result.cagr),
                    "mdd": float(result.mdd),
                    "sharpeRatio": float(result.sharpe_ratio) if result.sharpe_ratio else None,
                    "sortinoRatio": float(result.sortino_ratio) if result.sortino_ratio else None,
                    "volatility": float(result.volatility) if result.volatility else None,
                    "totalTrades": result.total_trades,
                    "winningTrades": result.winning_trades,
                    "losingTrades": result.losing_trades,
                    "winRate": float(result.win_rate) if result.win_rate else None,
                    "profitFactor": float(result.profit_factor) if result.profit_factor else None,
                    "avgWin": float(result.avg_win) if result.avg_win else None,
                    "avgLoss": float(result.avg_loss) if result.avg_loss else None,
                    "benchmarkReturn": float(result.benchmark_return) if result.benchmark_return is not None else None,
                    "alpha": float(result.alpha) if result.alpha is not None else None,
                    "beta": float(result.beta) if result.beta is not None else None,
                    "executionTimeSeconds": elapsed
                })

            logger.info(
                f"백테스트 완료: result_id={result_id}, "
                f"strategy={strategy_id}, trades={result.total_trades}, "
                f"mode={execution_type}"
            )

        except Exception as e:
            self._log_error("백테스트 실행", e)
            self._publish_failure(message, str(e), start_time)
            raise

    def _publish_failure(
        self,
        message: KafkaMessage,
        error_message: str,
        start_time: float
    ):
        """실패 이벤트 발행"""
        if self.publisher:
            payload = message.payload
            self.publisher.publish("BACKTEST_FAILED", {
                "status": "failed",
                "timestamp": datetime.now(KST).isoformat(),
                "requestId": message.request_id,
                "strategyId": payload.get("strategyId"),
                "startDate": payload.get("startDate"),
                "endDate": payload.get("endDate"),
                "initialCapital": payload.get("initialCapital", 10000000.0),
                "benchmark": payload.get("benchmark", DEFAULT_BENCHMARK),
                "userId": payload.get("userId"),
                "errorCode": "BACKTEST_EXECUTION_ERROR",
                "errorMessage": error_message,
                "retryable": True,
                "duration": time.time() - start_time
            })
