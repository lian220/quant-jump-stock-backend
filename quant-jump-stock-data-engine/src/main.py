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

import os
import logging
import time
import threading
from datetime import datetime
from pytz import timezone

from fastapi import FastAPI
import uvicorn

# Config
from config.settings import get_settings

# Legacy imports (점진적 교체 예정)
from core.database import MongoDB, PostgreSQL
from features.economic_data.router import router as economic_router
from features.economic_data.service import EconomicDataService
from services.recommendation_service import RecommendationService
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
    NewsCollectionHandler,
)
from adapter.input.rest import ml_router
from adapter.input.rest import analysis_router
from adapter.output.pubsub.publisher import PubSubPublisherAdapter
from adapter.output.postgresql.stock_repository import PostgresStockRepository
from adapter.output.postgresql.strategy_repository import PostgresStrategyRepository
from adapter.output.postgresql.backtest_repository import PostgresBacktestRepository
from adapter.output.mongodb.analysis_repository import (
    MongoPriceRepository,
    MongoAnalysisResultRepository,
)
from adapter.output.slack import SlackAnalysisNotifierAdapter

# New Application Services
from application.analysis import (
    TechnicalAnalysisApplicationService,
    RecommendationApplicationService,
)
from application.ml.prediction_service import PredictionService, GcpConfig
from application.backtest import BacktestApplicationService

KST = timezone('Asia/Seoul')

# Logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Settings
settings = get_settings()

# FastAPI (Read-Only Status API)
app = FastAPI(
    title="Quantiq Data Engine",
    description="Message Processing Worker (Hexagonal Architecture)"
)

app.include_router(economic_router)
app.include_router(ml_router.router)
app.include_router(analysis_router.router)


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
            "quantiq.news.collection.request",
        ],
        "timestamp": datetime.now(KST).isoformat()
    }


@app.get("/health")
def health_check():
    return {"status": "alive", "timestamp": datetime.now(KST).isoformat()}


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
            target_date=target_date
        )

        # 종합 분석 리포트: 기술적 + AI 예측 + 감정 분석
        if result.get("status") == "success":
            try:
                all_results = result.get("results", [])
                analysis_date = target_date or datetime.now().strftime("%Y-%m-%d")

                report = self._report_service.generate_report(all_results, analysis_date)
                SlackNotifier.notify_comprehensive_report(report)

                # 리포트 결과로 반환값 업데이트
                result["recommended_count"] = report.get("candidate_count", 0)
            except Exception as e:
                logger.error(f"종합 리포트 생성/전송 실패: {e}")
                # 분석 결과는 보존하고 계속 진행

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
        SlackNotifier.notify_economic_data_collection_start(request_id, source, thread_ts)

    def notify_success(self, request_id, summary, thread_ts):
        SlackNotifier.notify_economic_data_collection_success(request_id, summary, thread_ts)

    def notify_error(self, request_id, error, thread_ts):
        SlackNotifier.notify_economic_data_collection_error(request_id, error, thread_ts)


def _init_services():
    """서비스 및 핸들러 초기화 (push/pull 공통)

    Returns:
        tuple: (handlers_map, pubsub_publisher, vertexai_handler)
            handlers_map: {topic_str: handler.handle} 딕셔너리
            pubsub_publisher: PubSubPublisherAdapter
            vertexai_handler: VertexAIHandler 또는 None
    """
    # MongoDB 연결
    db = MongoDB.get_db()
    if db is None:
        raise RuntimeError("Failed to connect to MongoDB")

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
        publisher=pubsub_publisher
    )
    recommendation_handler = StockRecommendationHandler(
        publisher=pubsub_publisher
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

    # Vertex AI 핸들러 (GCP 활성화 시)
    vertexai_handler = None
    if settings.gcp.enabled:
        logger.info("Vertex AI enabled - initializing services...")
        try:
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
            logger.error(f"Failed to initialize Vertex AI services: {e}")
    else:
        logger.info("GCP disabled - Vertex AI services not available")

    # 뉴스 수집 서비스 초기화
    from adapter.output.external.saveticker_client import SaveTickerClient
    from adapter.output.mongodb.news_repository import MongoNewsRepository
    from adapter.output.postgresql.collector_state_repository import PostgresCollectorStateRepository
    from application.news.news_collection_service import NewsCollectionService

    saveticker_client = SaveTickerClient()
    news_mongo_repository = MongoNewsRepository(db)
    collector_state_repository = PostgresCollectorStateRepository(pool=pg_pool)
    news_collection_service = NewsCollectionService(
        saveticker_client=saveticker_client,
        news_repository=news_mongo_repository,
        collector_state_repository=collector_state_repository,
    )
    news_collection_handler = NewsCollectionHandler(
        service=news_collection_service,
        publisher=pubsub_publisher,
    )

    # 핸들러 맵 구성
    handlers_map = {
        economic_handler.topic: economic_handler.handle,
        technical_handler.topic: technical_handler.handle,
        sentiment_handler.topic: sentiment_handler.handle,
        recommendation_handler.topic: recommendation_handler.handle,
        backtest_handler.topic: backtest_handler.handle,
        news_collection_handler.topic: news_collection_handler.handle,
    }
    if vertexai_handler:
        handlers_map[vertexai_handler.topic] = vertexai_handler.handle

    return handlers_map, pubsub_publisher


def main():
    pubsub_mode = settings.pubsub.mode.lower()

    logger.info("=" * 80)
    logger.info(f"Quantiq Data Engine Started (mode={pubsub_mode})")
    logger.info("=" * 80)

    if pubsub_mode == "push":
        _run_push_mode()
    else:
        _run_pull_mode()


def _run_push_mode():
    """Push 모드: Cloud Run용 — FastAPI가 메인, Pub/Sub push 엔드포인트로 메시지 수신"""
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
    """Pull 모드: VM/로컬용 — Streaming pull이 메인, API는 별도 스레드"""
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
