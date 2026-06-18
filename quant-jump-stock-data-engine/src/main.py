"""
Quantiq Data Engine - Hexagonal Architecture

Primary: Pub/Sub 메시지 처리 워커
Secondary: REST API (헬스체크, ML 패키지 업로드)

Architecture:
    adapter/input/pubsub → application/strategy → adapter/output/*

실행 모드 (PUBSUB_MODE 환경변수):
    pull (기본): Streaming pull - VM/로컬 개발용
    push: HTTP push endpoint - Cloud Run용 (scale-to-zero 지원)
"""
from __future__ import annotations

import os
import logging
import time
import threading
from contextlib import asynccontextmanager
from datetime import datetime
from pytz import timezone

from typing import AsyncIterator, Optional

from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from fastapi.exception_handlers import (
    http_exception_handler,
    request_validation_exception_handler,
)
from starlette.exceptions import HTTPException as StarletteHTTPException
import uvicorn

# Config
from config.settings import get_settings

# Legacy imports (점진적 교체 예정)
from core.database import MongoDB, PostgreSQL
from features.economic_data.router import router as economic_router
from services.slack_notifier import SlackNotifier

# Hexagonal imports - Pub/Sub adapters
from adapter.input.pubsub.subscriber import PubSubSubscriberAdapter, PubSubMessage
from adapter.input.pubsub.push_handler import (
    router as push_handler_router,
    register_push_handler,
)
from adapter.input.pubsub.handlers import (
    EconomicDataHandler,
    TechnicalAnalysisHandler,
    SentimentAnalysisHandler,
    StockRecommendationHandler,
    StrategyExecutionHandler,
    VertexAIHandler,
    BacktestRequestHandler,
)
from adapter.input.rest import ml_router
from adapter.input.rest import analysis_router
from adapter.input.rest import price_history_router
from adapter.output.pubsub.publisher import PubSubPublisherAdapter
from adapter.output.postgresql.stock_repository import PostgresStockRepository
from adapter.output.postgresql.strategy_repository import PostgresStrategyRepository
from adapter.output.postgresql.backtest_repository import PostgresBacktestRepository
from adapter.output.mongodb.analysis_repository import (
    MongoPriceRepository,
    MongoAnalysisResultRepository,
)
from adapter.output.slack import SlackAnalysisNotifierAdapter
from adapter.output.core_api import CoreCacheClient

# Scoring SSoT — startup validation (fail-fast on spec misconfiguration)
from domain.recommendation.scoring_policy import ScoringPolicy
from domain.recommendation.exceptions import SpecValidationError, SpecNotFoundError

# Heavy 패키지(pandas, numpy, yfinance) Cold Start 최적화:
# EconomicDataService, RecommendationService, TechnicalAnalysisApplicationService,
# RecommendationApplicationService, BacktestApplicationService
# → _init_services()에서 lazy import

KST = timezone('Asia/Seoul')

# Logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Settings
settings = get_settings()


# ============================================================
# Scoring spec — startup validation (fail-fast)
# ============================================================
# Import-time side effect 제거 (PR 1 리뷰 반영, 2026-05-21):
# main.py 를 import 만 해도 spec 검증이 일어나면 tests/tooling 이
# spec 파일 없는 환경에서 import 못 함. 대신 (a) FastAPI lifespan 안에서
# Cloud Run startup 시 검증, (b) main() 진입 시 명시 호출로 pull-mode 도 보호.
def _validate_scoring_spec() -> None:
    """scoring_spec 로드 + invariant 검증. 실패 시 fail-fast (RuntimeError raise).

    실패 알림은 best-effort 로 Slack 에 발송 (PR 2). Slack 자체가 죽어도
    startup 실패는 그대로 propagate.
    """
    spec_path = os.environ.get("SCORING_SPEC_PATH", "<fallback>")
    try:
        p = ScoringPolicy.load_default()
        logger.info(
            "✅ scoring_spec loaded: version=%s, composite_max=%s, path=%s",
            p.formula_version,
            p.composite_max,
            spec_path,
        )
    except (SpecValidationError, SpecNotFoundError) as e:
        logger.critical("❌ scoring_spec load failed at startup: %s", e)
        _notify_spec_load_failure(spec_path, e)
        raise
    except Exception as e:
        logger.critical(
            "❌ scoring_spec load failed (unexpected): %s — Cloud Run startup probe fail",
            e,
        )
        _notify_spec_load_failure(spec_path, e)
        raise


def _notify_spec_load_failure(spec_path: str, error: Exception) -> None:
    """Slack 알림 best-effort — Slack 모듈 자체가 실패해도 startup raise 가 우선."""
    try:
        from services.slack_notifier import SlackNotifier
        SlackNotifier.notify_spec_load_failed(
            spec_path=spec_path,
            error=str(error),
            error_type=type(error).__name__,
        )
    except Exception as send_err:
        logger.warning("Spec 실패 Slack 알림 자체가 실패 (무시): %s", send_err)


@asynccontextmanager
async def _lifespan(_app: FastAPI) -> AsyncIterator[None]:
    """FastAPI lifespan — Cloud Run startup probe 실패로 즉시 차단."""
    _validate_scoring_spec()
    yield
    # shutdown phase 는 현재 명시 cleanup 없음


# FastAPI (Read-Only Status API)
app = FastAPI(
    title="Quantiq Data Engine",
    description="Message Processing Worker (Hexagonal Architecture)",
    lifespan=_lifespan,
)

app.include_router(economic_router)
app.include_router(ml_router.router)
app.include_router(analysis_router.router)
app.include_router(price_history_router.router)


# ============================================================
# 전역 예외 핸들러 — 5xx 발생 시 Slack 에러 채널 자동 알림
# ============================================================
# 핸들러 등록 순서 주의: HTTPException / RequestValidationError 를 명시적으로
# 먼저 등록하지 않으면, catch-all Exception 핸들러가 4xx 응답을 가로채서 모두
# 500 으로 응답되고 Slack 알림이 폭주할 수 있음 (Starlette/FastAPI 등록 순서
# 의존성). 명시 등록으로 짜잘한 4xx 는 기본 핸들러가 처리하도록 보장.

@app.exception_handler(StarletteHTTPException)
async def _http_exception_passthrough(request: Request, exc: StarletteHTTPException):
    return await http_exception_handler(request, exc)


@app.exception_handler(RequestValidationError)
async def _validation_exception_passthrough(request: Request, exc: RequestValidationError):
    return await request_validation_exception_handler(request, exc)


_unhandled_logger = logging.getLogger(__name__)


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    _unhandled_logger.exception(
        f"❌ Unhandled exception: {request.method} {request.url.path} - {exc}"
    )
    try:
        SlackNotifier.notify_handler_error(
            topic=f"HTTP {request.method} {request.url.path}",
            error=str(exc),
            error_type=type(exc).__name__,
            request_id=request.headers.get("x-request-id"),
            retryable=False,
        )
    except Exception as notify_err:
        _unhandled_logger.warning(f"Slack 에러 알림 실패: {notify_err}")
    return JSONResponse(
        status_code=500,
        content={"error": "Internal Server Error", "message": "Unexpected error"},
    )


@app.get("/")
def read_root():
    return {
        "service": "Quantiq Data Engine",
        "architecture": "Hexagonal (Ports & Adapters)",
        "status": "running",
        "subscribed_pubsub_topics": [
            "economic.data.update.request",
            "analysis.technical.request",
            "analysis.sentiment.request",
            "analysis.recommendation.request",
            "strategy.execution.request",
            "vertex.ai.run.request",
            "quantiq.backtest.request",
        ],
        "timestamp": datetime.now(KST).isoformat()
    }


@app.get("/health")
def health_check():
    return {"status": "alive", "timestamp": datetime.now(KST).isoformat()}


@app.post("/admin/freshness/check")
def freshness_check(date: Optional[str] = None, threshold: int = 30):
    """
    데이터 freshness 사전 점검 — 매일 23:00 KST Cloud Scheduler 트리거.

    2026-05-14 사고 회고:
      Vertex AI Job SUCCEEDED 인데 stock_predictions 0건. 운영자 미인지로
      다음날 23:20 KST 자연 발화 시 "추천 0개" 사용자 노출.
      본 endpoint 가 자연 발화 전 데이터 부재 감지 → 운영자 사전 통지.

    2026-06-11 버그 수정:
      (1) date 를 datetime(BSON Date) 범위로 쿼리했으나 실제 저장은 문자열 → 항상 0건
          (데이터 정상인 날도 0 보고하는 오탐). 문자열 정확매칭으로 변경.
      (2) 기본값을 'KST 오늘' → latest_complete_bar_date_str() 로 변경. US 장 마감 전
          KST 야간에 당일 데이터를 찾으면 아직 미생산이라 오탐.

    Args:
      date: YYYY-MM-DD (default: 최근 완성된 미국 거래일 바 = latest_complete_bar_date_str())
      threshold: 컬렉션별 최소 문서 수 (default: 30 — 약 42개 종목 중 일부 누락 허용)

    Returns:
      JSON: {status, check_date, stock_predictions_count, stock_recommendations_count,
             alert_sent}
    """
    from core.database import MongoDB
    from core.timezone import latest_complete_bar_date_str

    # 기본값: 최근 '완성된' 미국 거래일 바 (마감 버퍼 + 주말 롤백 처리).
    # 단순 KST today 를 쓰면 US 장 마감 전이라 당일 데이터가 아직 미생산 → 오탐.
    check_date = date or latest_complete_bar_date_str()

    db = MongoDB.get_db()
    # date 는 전 컬렉션에서 문자열 "YYYY-MM-DD" 로 저장됨 (2026-05-20 통일).
    # BSON 정렬상 String < Date 라 datetime 범위 쿼리는 항상 0건 매칭 → 문자열 정확매칭 사용.
    sp_count = db.stock_predictions.count_documents({"date": check_date})
    sr_count = db.stock_recommendations.count_documents({"date": check_date})

    alert_sent = False
    if sp_count < threshold or sr_count < threshold:
        try:
            SlackNotifier.notify_freshness_alert(
                check_date=check_date,
                stock_predictions_count=sp_count,
                stock_recommendations_count=sr_count,
                threshold=threshold,
            )
            alert_sent = True
        except Exception as e:
            logging.getLogger(__name__).warning(f"freshness 알림 전송 실패: {e}")

    return {
        "status": "alert" if alert_sent else "ok",
        "check_date": check_date,
        "stock_predictions_count": sp_count,
        "stock_recommendations_count": sr_count,
        "threshold": threshold,
        "alert_sent": alert_sent,
    }


@app.post("/api/v1/recommendations/sync")
def sync_recommendations(date: str):
    """MongoDB → PostgreSQL 동기화"""
    from application.recommendation.sync_service import RecommendationSyncService

    sync_service = RecommendationSyncService()
    result = sync_service.sync_latest_recommendations(date)
    return result


def run_api():
    """REST API 서버 실행 (별도 스레드)"""
    port = settings.server_port
    logger.info(f"Starting Data Engine API server on port {port}")
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="warning")


# ============================================================
# Legacy Adapter Wrappers
# 기존 서비스를 새 핸들러 프로토콜에 맞게 래핑
# ============================================================

class LegacyEconomicServiceAdapter:
    """기존 EconomicDataService를 새 프로토콜에 맞게 래핑"""

    def __init__(self, service: EconomicDataService):
        self._service = service

    def collect_economic_data(self, start_date=None, end_date=None):
        return self._service.collect_economic_data(start_date=start_date, end_date=end_date)


class LegacyTechnicalAnalysisAdapter:
    """기존 RecommendationService.run_technical_analysis 래핑"""

    def __init__(self, service: RecommendationService):
        self._service = service

    def run_technical_analysis(self, request_id, thread_ts, start_date=None, end_date=None):
        return self._service.run_technical_analysis(request_id, thread_ts, start_date=start_date, end_date=end_date)


class NewTechnicalAnalysisAdapter:
    """새 TechnicalAnalysisApplicationService를 핸들러 프로토콜에 맞게 래핑 (async→sync)"""

    def __init__(self, service: 'TechnicalAnalysisApplicationService'):
        from services.comprehensive_report import ComprehensiveReportService
        self._service = service
        self._report_service = ComprehensiveReportService()

    async def run_technical_analysis(self, request_id, thread_ts, start_date=None, end_date=None):
        target_date = end_date or start_date

        result = await self._service.analyze_stocks(
            request_id=request_id,
            thread_ts=thread_ts,
            target_date=target_date,
            start_date=start_date,
            end_date=end_date
        )

        return result


class LegacySentimentAnalysisAdapter:
    """기존 RecommendationService.run_sentiment_analysis 래핑"""

    def __init__(self, service: RecommendationService):
        self._service = service

    def run_sentiment_analysis(self, request_id, thread_ts, start_date=None, end_date=None):
        return self._service.run_sentiment_analysis(request_id, thread_ts, start_date=start_date, end_date=end_date)


class LegacySlackNotifierAdapter:
    """기존 SlackNotifier를 새 프로토콜에 맞게 래핑"""

    def notify_start(self, request_id, source, thread_ts):
        return SlackNotifier.notify_economic_data_collection_start(request_id, source, thread_ts)

    def notify_success(self, request_id, summary, thread_ts):
        SlackNotifier.notify_economic_data_collection_success(request_id, summary, thread_ts)

    def notify_error(self, request_id, error, thread_ts):
        SlackNotifier.notify_economic_data_collection_error(request_id, error, thread_ts)


def _init_services():
    """서비스 및 핸들러 초기화 (push/pull 공통)

    Returns:
        tuple: (handlers_map, pubsub_publisher)
            handlers_map: {topic_str: handler.handle} 딕셔너리
            pubsub_publisher: PubSubPublisherAdapter
    """
    # Heavy 패키지 lazy import (Cold Start 최적화: pandas ~14s, numpy ~2s, yfinance ~4s)
    from features.economic_data.service import EconomicDataService
    from services.recommendation_service import RecommendationService
    from application.analysis import (
        TechnicalAnalysisApplicationService,
        RecommendationApplicationService,
    )
    from application.backtest import BacktestApplicationService

    # MongoDB 연결
    db = MongoDB.get_db()
    if db is None:
        raise RuntimeError("Failed to connect to MongoDB")

    # MongoDB 인덱스 생성 (idempotent - 이미 존재하면 무시)
    MongoDB.ensure_indexes(db)

    # 서비스 초기화
    economic_service = LegacyEconomicServiceAdapter(EconomicDataService())
    slack_notifier = LegacySlackNotifierAdapter()

    # PostgreSQL 커넥션 풀 생성
    pg_pool = PostgreSQL.get_pool()

    # Hexagonal 분석 서비스
    stock_repository = PostgresStockRepository(pool=pg_pool)
    price_repository = MongoPriceRepository(db)
    result_repository = MongoAnalysisResultRepository(db)
    analysis_notifier = SlackAnalysisNotifierAdapter(settings)

    technical_app_service = TechnicalAnalysisApplicationService(
        stock_repository=stock_repository,
        price_repository=price_repository,
        result_repository=result_repository,
        notifier=analysis_notifier,
        lookback_days=180
    )

    _recommendation_app_service = RecommendationApplicationService(
        technical_service=technical_app_service,
        sentiment_service=None,
        notifier=analysis_notifier
    )

    technical_service = NewTechnicalAnalysisAdapter(technical_app_service)
    analysis_router.set_technical_service(technical_service)

    # 가격 시계열(OHLCV + 지표) 조회 서비스 — composition root 에서 output adapter 주입
    from application.analysis.price_history_service import PriceHistoryService
    price_history_router.set_price_history_service(
        PriceHistoryService(price_repository)
    )

    recommendation_service = RecommendationService()
    sentiment_service = LegacySentimentAnalysisAdapter(recommendation_service)

    # Pub/Sub Publisher
    pubsub_publisher = PubSubPublisherAdapter(settings)

    # 핸들러 생성
    economic_handler = EconomicDataHandler(
        service=economic_service,
        notifier=slack_notifier,
        publisher=pubsub_publisher
    )
    technical_handler = TechnicalAnalysisHandler(
        service=technical_service,
        publisher=pubsub_publisher
    )
    sentiment_handler = SentimentAnalysisHandler(
        service=sentiment_service,
        notifier=analysis_notifier,
        publisher=pubsub_publisher
    )
    # Core API 캐시 클라이언트
    cache_client = CoreCacheClient(base_url=settings.core_api.base_url)

    recommendation_handler = StockRecommendationHandler(
        publisher=pubsub_publisher,
        cache_client=cache_client,
    )

    # 백테스트 핸들러
    strategy_pg_repository = PostgresStrategyRepository(pool=pg_pool)
    backtest_repository = PostgresBacktestRepository(pool=pg_pool)
    backtest_service = BacktestApplicationService(
        strategy_repository=strategy_pg_repository
    )
    backtest_handler = BacktestRequestHandler(
        backtest_service=backtest_service,
        backtest_repository=backtest_repository,
        publisher=pubsub_publisher
    )

    # Vertex AI 핸들러 (VERTEX_AI_ENABLED 환경변수로 제어)
    vertexai_handler = None
    if settings.gcp.enabled:  # VERTEX_AI_ENABLED
        # 필수 설정 검증
        missing = []
        if not settings.gcp.project_id:
            missing.append("VERTEX_AI_PROJECT_ID")
        if not settings.gcp.model_bucket:
            missing.append("VERTEX_AI_BUCKET_NAME")
        if not settings.gcp.region:
            missing.append("VERTEX_AI_REGION")

        if missing:
            logger.warning(f"Vertex AI 필수 설정 누락: {', '.join(missing)} — Vertex AI 비활성화")
        else:
            logger.info("Vertex AI enabled (VERTEX_AI_ENABLED=true) - initializing services...")
            try:
                from application.ml.prediction_service import PredictionService, GcpConfig
                gcp_config = GcpConfig(
                    project_id=settings.gcp.project_id,
                    region=settings.gcp.region,
                    bucket_name=settings.gcp.model_bucket,
                    package_base_path=settings.gcp.package_base_path,
                    credentials_path=settings.gcp.credentials_path,
                    job_name=settings.gcp.job_name,
                    machine_type=settings.gcp.machine_type,
                    accelerator_type=settings.gcp.accelerator_type,
                    accelerator_count=settings.gcp.accelerator_count,
                    container_uri=settings.gcp.container_uri
                )
                prediction_service = PredictionService(config=gcp_config)
                ml_router.set_prediction_service(prediction_service)
                vertexai_handler = VertexAIHandler(
                    service=prediction_service,
                    publisher=pubsub_publisher
                )
                logger.info("Vertex AI services initialized")
            except Exception as e:
                logger.exception(f"Failed to initialize Vertex AI services: {e}")
    else:
        logger.info("Vertex AI disabled (VERTEX_AI_ENABLED=false)")

    # 핸들러 맵 구성
    handlers_map = {
        economic_handler.topic: economic_handler.handle,
        technical_handler.topic: technical_handler.handle,
        sentiment_handler.topic: sentiment_handler.handle,
        recommendation_handler.topic: recommendation_handler.handle,
        backtest_handler.topic: backtest_handler.handle,
    }
    if vertexai_handler:
        handlers_map[vertexai_handler.topic] = vertexai_handler.handle
    else:
        # VERTEX_AI_ENABLED=false 여도 토픽 구독 → 파이프라인 체이닝 메시지 ACK 처리
        async def _vertex_ai_noop(message):
            logger.info(f"⏭️ Vertex AI 비활성화 — 메시지 스킵 (request={message.request_id})")
        handlers_map["vertex.ai.run.request"] = _vertex_ai_noop

    return handlers_map, pubsub_publisher


def main():
    # Spec 검증 — push 모드는 lifespan 이 한 번 더 보장하지만, pull 모드는
    # subscriber 가 API thread 와 무관하게 시작하므로 진입 시 명시 검증 필요.
    _validate_scoring_spec()

    pubsub_mode = settings.pubsub.mode.lower()

    logger.info("=" * 80)
    logger.info(f"Quantiq Data Engine Started (mode={pubsub_mode})")
    logger.info("=" * 80)

    if pubsub_mode == "push":
        _run_push_mode()
    else:
        _run_pull_mode()


def _run_push_mode():
    """Push 모드: Cloud Run용 — FastAPI가 메인, Pub/Sub push 엔드포인트로 메시지 수신

    Cloud Scheduler가 Pub/Sub 메시지를 직접 발행하여 파이프라인 트리거.
    """
    logger.info("Starting in PUSH mode (Cloud Run)")

    # 서비스 초기화
    handlers_map, pubsub_publisher = _init_services()

    # Push 핸들러 등록
    for topic, handler in handlers_map.items():
        register_push_handler(topic, handler)

    # Push 라우터를 FastAPI 앱에 등록
    app.include_router(push_handler_router)

    logger.info(f"Push handlers registered for topics: {list(handlers_map.keys())}")

    # Cloud Run은 PORT 환경변수 사용 (기본 8080)
    port = int(os.environ.get("PORT", settings.server_port))
    logger.info(f"Starting FastAPI server on port {port}")
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")


def _run_pull_mode():
    """Pull 모드: VM/로컬용 — Streaming pull (Cloud Scheduler가 파이프라인 트리거)"""
    logger.info("Starting in PULL mode (VM/Local)")

    # API 서버 시작 (별도 스레드)
    api_thread = threading.Thread(target=run_api, daemon=True)
    api_thread.start()

    # Pub/Sub 에뮬레이터 연결 대기
    logger.info("Waiting for Pub/Sub to be ready...")
    time.sleep(2)

    # 서비스 초기화
    handlers_map, pubsub_publisher = _init_services()

    # Subscriber 설정
    subscriber = PubSubSubscriberAdapter(settings=settings)

    for topic, handler in handlers_map.items():
        subscriber.register_handler(topic, handler)

    topics = list(handlers_map.keys())
    logger.info(f"Subscribing to topics: {topics}")

    try:
        subscriber.start(topics)
        logger.info("Starting Pub/Sub streaming pull...")
        subscriber.run_forever()
    except KeyboardInterrupt:
        logger.info("Shutdown requested...")
    finally:
        subscriber.stop()
        pubsub_publisher.close()
        PostgreSQL.close_pool()
        logger.info("Data Engine stopped")


if __name__ == "__main__":
    main()
