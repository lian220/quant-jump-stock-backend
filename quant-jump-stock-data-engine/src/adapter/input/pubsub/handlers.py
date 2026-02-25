"""
Pub/Sub Message Handlers

토픽별 메시지 처리 핸들러.
각 핸들러는 Application 서비스를 호출하여 비즈니스 로직 실행.
"""

import logging
import time
from abc import ABC, abstractmethod
from decimal import Decimal
from typing import Optional, Protocol
from datetime import datetime, date, timedelta
from pytz import timezone

from application.recommendation.sync_service import RecommendationSyncService

# Cold Start 최적화: application.backtest.service → pandas/numpy 전체 로드
# BacktestCheckpoint, _DEFAULT_BENCHMARK는 BacktestRequestHandler 내부에서 lazy import
_DEFAULT_BENCHMARK = "SPY"
from core.database import MongoDB
from services.comprehensive_report import ComprehensiveReportService
from services.slack_notifier import SlackNotifier
from .subscriber import PubSubMessage, NonRetryableError

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
    def handle(self, message: PubSubMessage) -> None:
        """메시지 처리"""
        pass

    def _log_start(self, message: PubSubMessage, description: str) -> float:
        """처리 시작 로깅"""
        date_info = self._format_date_range(message.start_date, message.end_date)
        logger.debug(f"{description} 수신 (request={message.request_id}, date={date_info})")
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
    def notify_start(self, request_id: str, source: str, thread_ts: Optional[str]) -> Optional[str]:
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

    def handle(self, message: PubSubMessage) -> None:
        start_time = self._log_start(message, "경제 데이터 업데이트")

        # 수집 시작 알림 (Bot Token 사용 시 thread_ts 반환)
        thread_ts = message.thread_ts
        if self.notifier:
            source = message.payload.get("source", "pubsub")
            new_ts = self.notifier.notify_start(message.request_id, source, thread_ts)
            if new_ts:
                thread_ts = new_ts

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
                self.notifier.notify_success(message.request_id, summary, thread_ts)

            if self.publisher:
                self.publisher.publish("ECONOMIC_DATA_UPDATED", {
                    "status": "success",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "duration": elapsed
                })

        except (ValueError, KeyError, TypeError) as e:
            self._log_error("경제 데이터 수집 (재시도 불가 - 데이터 오류)", e)

            if self.notifier:
                self.notifier.notify_error(message.request_id, str(e), thread_ts)

            if self.publisher:
                self.publisher.publish("ECONOMIC_DATA_UPDATE_FAILED", {
                    "status": "failed",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "error": str(e)
                })
            raise NonRetryableError(f"경제 데이터 수집 데이터 오류: {e}") from e

        except Exception as e:
            self._log_error("경제 데이터 수집", e)

            if self.notifier:
                self.notifier.notify_error(message.request_id, str(e), thread_ts)

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

    async def handle(self, message: PubSubMessage) -> None:
        start_time = self._log_start(message, "기술적 분석 요청")

        try:
            result = await self.service.run_technical_analysis(
                message.request_id,
                message.thread_ts,
                start_date=message.start_date,
                end_date=message.end_date
            )

            self._log_success("기술적 분석", start_time)

            # 🆕 MongoDB → PostgreSQL 동기화 (분석 완료 후)
            try:
                sync_service = RecommendationSyncService()
                analyzed_dates = sorted({
                    row.get("date")
                    for row in result.get("results", [])
                    if isinstance(row, dict) and row.get("date")
                })

                # 결과에서 날짜를 추출하지 못하면 기존 단일 날짜 fallback
                if not analyzed_dates:
                    analyzed_dates = [message.end_date or message.start_date or date.today().isoformat()]

                total_synced = 0
                for analysis_date in analyzed_dates:
                    sync_result = sync_service.sync_latest_recommendations(analysis_date)
                    synced_count = sync_result.get("synced_count", 0)
                    total_synced += synced_count
                    logger.info(f"🔄 동기화 완료 (date={analysis_date}): {synced_count}개 종목")

                logger.info(
                    f"🔄 전체 동기화 완료: {total_synced}개 종목 "
                    f"(대상일={len(analyzed_dates)}일, {analyzed_dates[0]}~{analyzed_dates[-1]})"
                )
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

        except (ValueError, KeyError, TypeError) as e:
            self._log_error("기술적 분석 (재시도 불가 - 데이터 오류)", e)

            if self.publisher:
                self.publisher.publish("ANALYSIS_TECHNICAL_FAILED", {
                    "status": "failed",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "error": str(e)
                })
            raise NonRetryableError(f"기술적 분석 데이터 오류: {e}") from e

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
    """감정 분석 핸들러 (Slack 스레드 답글 공통화)"""

    def __init__(
        self,
        service: SentimentAnalysisServiceProtocol,
        notifier=None,
        publisher: Optional[EventPublisherProtocol] = None
    ):
        self.service = service
        self.notifier = notifier
        self.publisher = publisher

    @property
    def topic(self) -> str:
        return "analysis.sentiment.request"

    async def handle(self, message: PubSubMessage) -> None:
        start_time = self._log_start(message, "뉴스 감정 분석 요청")

        thread_ts = message.thread_ts

        # 시작 알림 (기술적 분석과 동일한 패턴)
        if self.notifier:
            try:
                new_ts = await self.notifier.notify_analysis_start("sentiment", thread_ts)
                if new_ts:
                    thread_ts = new_ts
            except Exception as e:
                logger.warning(f"감정 분석 시작 알림 실패: {e}")

        try:
            result = self.service.run_sentiment_analysis(
                message.request_id,
                message.thread_ts,
                start_date=message.start_date,
                end_date=message.end_date
            )

            elapsed = time.time() - start_time
            self._log_success("뉴스 감정 분석", start_time)

            # 완료 알림 (기술적 분석과 동일한 패턴)
            if self.notifier:
                total_analyzed = len(result) if isinstance(result, list) else result.get("total_analyzed", 0) if isinstance(result, dict) else 0
                try:
                    await self.notifier.notify_analysis_complete(
                        "sentiment", total_analyzed, 0, thread_ts
                    )
                except Exception as e:
                    logger.warning(f"감정 분석 완료 알림 실패: {e}")

            if self.publisher:
                self.publisher.publish("ANALYSIS_SENTIMENT_COMPLETED", {
                    "status": "success",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "duration": elapsed,
                    "result": result
                })

        except (ValueError, KeyError, TypeError) as e:
            self._log_error("뉴스 감정 분석 (재시도 불가 - 데이터 오류)", e)

            if self.notifier:
                try:
                    await self.notifier.notify_analysis_error("sentiment", str(e), thread_ts)
                except Exception:
                    pass

            if self.publisher:
                self.publisher.publish("ANALYSIS_SENTIMENT_FAILED", {
                    "status": "failed",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "error": str(e)
                })
            raise NonRetryableError(f"뉴스 감정 분석 데이터 오류: {e}") from e

        except Exception as e:
            self._log_error("뉴스 감정 분석", e)

            if self.notifier:
                try:
                    await self.notifier.notify_analysis_error("sentiment", str(e), thread_ts)
                except Exception:
                    pass

            if self.publisher:
                self.publisher.publish("ANALYSIS_SENTIMENT_FAILED", {
                    "status": "failed",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "error": str(e)
                })
            raise


class StockRecommendationHandler(MessageHandler):
    """종목 추천 핸들러 (Composite Score 계산)"""

    def __init__(
        self,
        publisher: Optional[EventPublisherProtocol] = None
    ):
        self.publisher = publisher

    @property
    def topic(self) -> str:
        return "analysis.recommendation.request"

    @staticmethod
    def _parse_date(value: str) -> date:
        """YYYY-MM-DD 문자열을 date 객체로 변환"""
        return datetime.strptime(value, "%Y-%m-%d").date()

    def _resolve_analysis_dates(
        self,
        start_date: Optional[str],
        end_date: Optional[str]
    ) -> list[str]:
        """요청 파라미터를 실제 처리할 분석 날짜 목록으로 변환"""
        today = date.today()

        if start_date and end_date:
            start = self._parse_date(start_date)
            end = self._parse_date(end_date)
            if start > end:
                raise ValueError(f"startDate({start_date})가 endDate({end_date})보다 클 수 없습니다")
        elif start_date:
            start = self._parse_date(start_date)
            end = today
            if start > end:
                raise ValueError(f"startDate({start_date})가 오늘({today.isoformat()})보다 클 수 없습니다")
        elif end_date:
            single = self._parse_date(end_date)
            return [single.isoformat()]
        else:
            return [today.isoformat()]

        total_days = (end - start).days
        return [
            (start + timedelta(days=offset)).isoformat()
            for offset in range(total_days + 1)
            if (start + timedelta(days=offset)).weekday() < 5  # 주말 제외
        ]

    def handle(self, message: PubSubMessage) -> None:
        start_time = self._log_start(message, "종목 추천 실행")

        try:
            sync_service = RecommendationSyncService()
            analysis_dates = self._resolve_analysis_dates(message.start_date, message.end_date)
            logger.info(f"종목 추천 처리 날짜 수: {len(analysis_dates)}일 ({analysis_dates[0]} ~ {analysis_dates[-1]})")

            # 스케줄러 채널: 시작 알림 (thread_ts 반환 시 이후 완료 메시지에 사용)
            thread_ts = SlackNotifier.notify_recommendation_start(
                analysis_dates, thread_ts=message.thread_ts
            )

            total_synced_count = 0
            sync_results = []
            total_candidate_count = 0
            total_near_miss_count = 0

            report_db = MongoDB.get_db()
            report_service = ComprehensiveReportService()

            for analysis_date in analysis_dates:
                result = sync_service.sync_latest_recommendations(analysis_date)
                synced_count = int(result.get("synced_count", 0) or 0)
                total_synced_count += synced_count
                sync_results.append({
                    "analysisDate": analysis_date,
                    "status": result.get("status"),
                    "syncedCount": synced_count
                })

                # 종합 분석 리포트: 기술적 + AI 예측 + 감정 분석 결합 후 Slack 발송
                try:
                    # ISODate range query 우선, string 날짜 fallback
                    dt_date = datetime.strptime(analysis_date, "%Y-%m-%d")
                    start_utc = datetime(dt_date.year, dt_date.month, dt_date.day, 0, 0, 0)
                    end_utc = start_utc + timedelta(days=1)
                    tech_docs = list(report_db.stock_recommendations.find(
                        {"date": {"$gte": start_utc, "$lt": end_utc}},
                        {"_id": 0, "ticker": 1, "stock_name": 1, "date": 1,
                         "technical_indicators": 1, "is_recommended": 1}
                    ))
                    if not tech_docs:
                        tech_docs = list(report_db.stock_recommendations.find(
                            {"date": analysis_date},
                            {"_id": 0, "ticker": 1, "stock_name": 1, "date": 1,
                             "technical_indicators": 1, "is_recommended": 1}
                        ))
                    logger.info(f"종합 리포트: 기술적 분석 {len(tech_docs)}개 종목 ({analysis_date})")
                    # tech_docs 없어도 AI/감정 데이터로 리포트 생성 + Slack 발송
                    report = report_service.generate_report(tech_docs, analysis_date)
                    candidate_count = len(report.get("buy_candidates", []))
                    near_miss_count = len(report.get("near_miss_candidates", []))
                    total_candidate_count += candidate_count
                    total_near_miss_count += near_miss_count

                    # 분석 채널: 종합 리포트 독립 메시지
                    SlackNotifier.notify_comprehensive_report(report, thread_ts=None)
                    logger.info(f"종합 리포트 Slack 발송 완료 ({analysis_date}): 추천 {candidate_count}개, 근접 탈락 {near_miss_count}개")
                except Exception as e:
                    logger.exception(f"종합 리포트 생성/전송 실패 ({analysis_date}): {e}")

            # 스케줄러 채널: 완료 알림
            elapsed = time.time() - start_time
            SlackNotifier.notify_recommendation_complete(
                analysis_date=analysis_dates[-1],
                synced_count=total_synced_count,
                candidate_count=total_candidate_count,
                near_miss_count=total_near_miss_count,
                elapsed=elapsed,
                thread_ts=thread_ts,
            )

            self._log_success("종목 추천", start_time)

            if self.publisher:
                self.publisher.publish("STOCK_RECOMMENDATION_COMPLETED", {
                    "status": "success",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "syncedCount": total_synced_count,
                    "analysisDates": analysis_dates,
                    "syncResults": sync_results,
                    "duration": time.time() - start_time
                })

        except Exception as e:
            self._log_error("종목 추천", e)

            if self.publisher:
                self.publisher.publish("STOCK_RECOMMENDATION_FAILED", {
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

    async def handle(self, message: PubSubMessage) -> None:
        start_time = self._log_start(message, "전략 실행 요청")

        try:
            # 메시지에서 종목 목록 추출
            symbols = message.payload.get("symbols", [])
            strategy_ids = message.payload.get("strategyIds")

            if not symbols:
                raise ValueError("symbols 필드가 필요합니다")

            from application.ports.input_ports import ExecutionRequest
            request = ExecutionRequest(
                symbols=symbols,
                strategy_ids=strategy_ids,
                parallel=True
            )
            result = await self.service.execute_batch(request)

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


class NewsCollectionServiceProtocol(Protocol):
    """뉴스 수집 서비스 프로토콜"""
    def collect(self, source: str = "SAVETICKER") -> dict:
        ...


class NewsCollectionHandler(MessageHandler):
    """뉴스 수집 핸들러 (Data Engine에서 수집 실행)"""

    def __init__(
        self,
        service: NewsCollectionServiceProtocol,
        publisher: Optional[EventPublisherProtocol] = None,
    ):
        self.service = service
        self.publisher = publisher

    @property
    def topic(self) -> str:
        return "quantiq.news.collection.request"

    def handle(self, message: PubSubMessage) -> None:
        start_time = self._log_start(message, "뉴스 수집 요청")

        source = message.payload.get("source", "SAVETICKER")

        try:
            result = self.service.collect(source=source)

            self._log_success("뉴스 수집", start_time)

            if self.publisher:
                self.publisher.publish("NEWS_COLLECTED", {
                    "status": "success",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "source": source,
                    "articleIds": result.get("article_ids", []),
                    "collectedCount": result.get("collected_count", 0),
                    "duration": time.time() - start_time,
                })

        except Exception as e:
            self._log_error("뉴스 수집", e)

            if self.publisher:
                self.publisher.publish("NEWS_COLLECTION_FAILED", {
                    "status": "failed",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "source": source,
                    "error": str(e),
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
        benchmark: str = _DEFAULT_BENCHMARK
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
        benchmark: str = _DEFAULT_BENCHMARK,
        benchmarks: Optional[list] = None,
        user_id: Optional[int] = None,
        risk_settings: Optional[dict] = None,
        position_sizing: Optional[dict] = None,
        trading_costs: Optional[dict] = None,
        preloaded_data: Optional[dict] = None,
        preloaded_benchmark: Optional[dict] = None
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

    Pub/Sub 메시지를 받아 Vertex AI Custom Job을 실행합니다.
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

    # 재시도해도 성공할 수 없는 에러 패턴
    _NON_RETRYABLE_PATTERNS = [
        "permission denied", "403", "unauthorized", "401",
        "not found", "404", "invalid", "credentials",
        "quota exceeded", "billing", "disabled",
    ]

    def _is_non_retryable(self, error_message: str) -> bool:
        """재시도 불가능한 에러인지 판별"""
        lower = error_message.lower()
        return any(p in lower for p in self._NON_RETRYABLE_PATTERNS)

    def _notify_job_submitted(self, thread_ts: str, result, start_time: float, env_vars: dict):
        """Job 제출 완료 시 Slack 스레드에 답글"""
        try:
            fine_tune_mode = env_vars.get("FINE_TUNE_MODE", "true")
            mode = "Fine-tuning" if fine_tune_mode == "true" else "Full Training"
            elapsed = f"{time.time() - start_time:.1f}초"

            SlackNotifier.notify_job_submitted(
                thread_ts=thread_ts,
                job_name=result.job_name or "N/A",
                mode=mode,
                elapsed=elapsed,
            )
        except Exception as e:
            logger.warning(f"Slack 답글 발송 실패 (Job은 정상 제출됨): {e}")

    def handle(self, message: PubSubMessage) -> None:
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

                # Slack 스레드 답글: "Job 제출 완료"
                if thread_ts:
                    self._notify_job_submitted(thread_ts, result, start_time, env_vars)

                if self.publisher:
                    self.publisher.publish("VERTEX_AI_JOB_SUBMITTED", {
                        "status": "success",
                        "timestamp": datetime.now(KST).isoformat(),
                        "requestId": message.request_id,
                        "jobName": result.job_name,
                        "duration": time.time() - start_time
                    })
            else:
                # 실패 원인 분석: 재시도 가능 여부 판별
                error_msg = result.message or "Unknown error"
                if self._is_non_retryable(error_msg):
                    raise NonRetryableError(f"Vertex AI 비재시도성 에러: {error_msg}")
                raise RuntimeError(error_msg)

        except NonRetryableError as e:
            # NonRetryableError는 실패 이벤트 발행 후 그대로 re-raise → subscriber가 ACK 처리
            self._log_error("Vertex AI 예측 실행 (재시도 불가)", e)

            if self.publisher:
                self.publisher.publish("VERTEX_AI_JOB_FAILED", {
                    "status": "failed",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "error": str(e),
                    "retryable": False
                })
            raise

        except Exception as e:
            self._log_error("Vertex AI 예측 실행", e)

            if self.publisher:
                self.publisher.publish("VERTEX_AI_JOB_FAILED", {
                    "status": "failed",
                    "timestamp": datetime.now(KST).isoformat(),
                    "requestId": message.request_id,
                    "error": str(e),
                    "retryable": True
                })
            raise


class BacktestRequestHandler(MessageHandler):
    """
    백테스트 실행 요청 핸들러

    SCRUM-186: Pub/Sub으로 백테스트 요청을 받아 실행하고
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

    async def handle(self, message: PubSubMessage) -> None:
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
        benchmark = payload.get("benchmark", _DEFAULT_BENCHMARK)
        # SCRUM-337: 다중 벤치마크 지원
        benchmarks = payload.get("benchmarks", [benchmark])
        # SCRUM-330: 리스크 관리 파라미터 수신
        risk_settings = payload.get("riskSettings")
        position_sizing = payload.get("positionSizing")
        trading_costs = payload.get("tradingCosts")
        # SCRUM-344: 유니버스 타입 + 백테스트 분류
        universe_type = payload.get("universeType", "MARKET")
        backtest_type = payload.get("backtestType", "USER_CUSTOM")
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
            # [개선] 백테스트 실행 전 데이터 존재 여부 확인 + 데이터 로드 (중복 방지)
            # MongoDB에서 요청된 종목의 데이터가 있는지 검증
            from application.backtest.data_loader_mongo import MongoDataLoader
            import os

            # 사전 로드된 데이터 저장 변수
            preloaded_data = None
            preloaded_benchmark = None

            try:
                data_loader = MongoDataLoader(
                    uri=os.environ.get("MONGODB_URI"),
                    database=os.environ.get("MONGODB_DB_NAME", "stock_trading")
                )

                # 시작일과 종료일 사이에 데이터가 있는지 확인
                from datetime import datetime
                start_dt = datetime.strptime(start_date, "%Y-%m-%d").date()
                end_dt = datetime.strptime(end_date, "%Y-%m-%d").date()

                # 전체 종목을 한번에 조회하여 데이터 존재 여부 확인 (1회 쿼리)
                all_data = data_loader.load(tickers, start_dt, end_dt)
                missing_tickers = [t for t in tickers if t not in all_data or all_data[t].empty]
                available_tickers = [t for t in tickers if t not in missing_tickers]

                if missing_tickers:
                    logger.warning(
                        f"데이터 없는 종목 {len(missing_tickers)}개 제외하고 진행: "
                        f"{', '.join(missing_tickers)}"
                    )

                if not available_tickers:
                    error_msg = f"백테스트 가능한 종목이 없습니다 (요청 종목 전부 데이터 없음: {', '.join(tickers)})"
                    logger.error(error_msg)
                    self._publish_failure(message, error_msg, start_time)
                    raise ValueError(error_msg)

                # 데이터 있는 종목만으로 계속 진행
                tickers = available_tickers
                logger.info(f"백테스트 진행 종목: {len(tickers)}개 {tickers}")

                # 🆕 로드한 데이터 저장 (재사용)
                preloaded_data = all_data

                # 🆕 벤치마크 데이터도 미리 로드
                if benchmark:
                    try:
                        preloaded_benchmark = data_loader.load_benchmark(
                            benchmark_ticker=benchmark,
                            start_date=start_dt,
                            end_date=end_dt
                        )
                        if preloaded_benchmark is not None:
                            logger.info(f"벤치마크 데이터 사전 로드 완료: {benchmark}")
                    except Exception as bench_err:
                        logger.warning(f"벤치마크 로드 실패 (계속 진행): {bench_err}")

            except ValueError:
                raise  # missing tickers → propagate to caller
            except Exception as data_check_error:
                logger.error(f"데이터 존재 여부 확인 중 오류: {data_check_error}")
                # 데이터 체크 실패 시에도 백테스트는 진행 (기존 동작 유지)
                logger.warning("데이터 체크를 건너뛰고 백테스트를 진행합니다")

            async def _execute():
                existing_backtest = None
                checkpoint = None
                equity_curve_data = None

                # 강제 전체 실행이 아닌 경우, 기존 백테스트 및 체크포인트 조회
                # USER_CUSTOM은 항상 새 레코드 생성 (회차별 저장 보장)
                # CANONICAL만 증분 업데이트 허용 (전략당 1개 유지)
                is_canonical = backtest_type == "CANONICAL"
                if not force_full and is_canonical:
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
                # 🆕 사전 로드된 데이터 전달 (중복 로드 방지)
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
                    benchmarks=benchmarks,
                    user_id=user_id,
                    risk_settings=risk_settings,
                    position_sizing=position_sizing,
                    trading_costs=trading_costs,
                    preloaded_data=preloaded_data,
                    preloaded_benchmark=preloaded_benchmark,
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
                    # 전체: 새 결과 저장 (user_id + SCRUM-344 메타데이터 포함)
                    result.user_id = user_id
                    result.metadata["universe_type"] = universe_type
                    result.metadata["backtest_type"] = backtest_type
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

                required_keys = ["cash", "high_watermark", "positions", "trade_count"]
                missing = [k for k in required_keys if k not in checkpoint_data]
                if missing:
                    logger.warning(f"checkpoint_data에 필수 키 누락: {missing}. 체크포인트 저장을 건너뜁니다. backtest_id={result_id}")
                    return result, result_id, incremental_result.is_incremental

                from adapter.output.postgresql.backtest_repository import BacktestCheckpoint
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

            result, result_id, is_incremental = await _execute()

            elapsed = time.time() - start_time
            execution_type = "증분" if is_incremental else "전체"
            self._log_success(f"백테스트 실행 ({execution_type})", start_time)

            # Slack 알림 전송
            try:
                from services.slack_notifier import SlackNotifier
                SlackNotifier.notify_backtest_completed(
                    strategy_id=strategy_id,
                    strategy_name=result.strategy_name,
                    request_id=message.request_id,
                    backtest_type=f"{execution_type} (CANONICAL)" if payload.get("backtestType") == "CANONICAL" else execution_type,
                    start_date=start_date,
                    end_date=end_date,
                    total_return_pct=float((result.total_return / result.initial_capital) * 100),
                    cagr=float(result.cagr),
                    mdd=float(result.mdd),
                    sharpe_ratio=float(result.sharpe_ratio) if result.sharpe_ratio is not None else 0.0,
                    total_trades=result.total_trades,
                    win_rate=float(result.win_rate) if result.win_rate is not None else 0.0,
                    execution_time=elapsed
                )
            except Exception as slack_error:
                logger.warning(f"Slack 알림 전송 실패 (백테스트는 완료됨): {slack_error}")

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
                    "sharpeRatio": float(result.sharpe_ratio) if result.sharpe_ratio is not None else None,
                    "sortinoRatio": float(result.sortino_ratio) if result.sortino_ratio is not None else None,
                    "volatility": float(result.volatility) if result.volatility is not None else None,
                    "totalTrades": result.total_trades,
                    "winningTrades": result.winning_trades,
                    "losingTrades": result.losing_trades,
                    "winRate": float(result.win_rate) if result.win_rate is not None else None,
                    "profitFactor": float(result.profit_factor) if result.profit_factor is not None else None,
                    "avgWin": float(result.avg_win) if result.avg_win is not None else None,
                    "avgLoss": float(result.avg_loss) if result.avg_loss is not None else None,
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
        message: PubSubMessage,
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
                "benchmark": payload.get("benchmark", _DEFAULT_BENCHMARK),
                "userId": payload.get("userId"),
                "errorCode": "BACKTEST_EXECUTION_ERROR",
                "errorMessage": error_message,
                "retryable": True,
                "duration": time.time() - start_time
            })
