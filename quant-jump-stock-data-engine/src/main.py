"""
Quantiq Data Engine - Hexagonal Architecture

Primary: Kafka 메시지 처리 워커
Secondary: REST API (헬스체크, ML 패키지 업로드)

Architecture:
    adapter/input/kafka → application/strategy → adapter/output/*
"""

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
from features.ml_package.router import router as ml_package_router
from features.economic_data.service import EconomicDataService
from services.recommendation_service import RecommendationService
from services.slack_notifier import SlackNotifier

# New Hexagonal imports
from adapter.input.kafka.consumer import KafkaConsumerAdapter, KafkaMessage
from adapter.input.kafka.handlers import (
    EconomicDataHandler,
    TechnicalAnalysisHandler,
    SentimentAnalysisHandler,
    StrategyExecutionHandler,
    VertexAIHandler,
    BacktestRequestHandler,
)
from adapter.input.rest import ml_router
from adapter.input.rest import analysis_router
from adapter.output.kafka.producer import KafkaProducerAdapter
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
app.include_router(ml_package_router)
app.include_router(ml_router.router)
app.include_router(analysis_router.router)


@app.get("/")
def read_root():
    return {
        "service": "Quantiq Data Engine",
        "architecture": "Hexagonal (Ports & Adapters)",
        "status": "running",
        "subscribed_kafka_topics": [
            "economic.data.update.request",
            "analysis.technical.request",
            "analysis.sentiment.request",
            "strategy.execution.request",
            "vertex.ai.run.request",
            "quantiq.backtest.request",
        ],
        "timestamp": datetime.now(KST).isoformat()
    }


@app.get("/health")
def health_check():
    return {"status": "alive", "timestamp": datetime.now(KST).isoformat()}


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

    def run_technical_analysis(self, request_id, thread_ts, start_date=None, end_date=None):
        import asyncio

        target_date = end_date or start_date

        async def _run():
            return await self._service.analyze_stocks(
                request_id=request_id,
                thread_ts=thread_ts,
                target_date=target_date
            )

        try:
            loop = asyncio.get_event_loop()
        except RuntimeError:
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)

        result = loop.run_until_complete(_run())

        # 종합 분석 리포트: 기술적 + AI 예측 + 감정 분석
        if result.get("status") == "success":
            all_results = result.get("results", [])
            analysis_date = target_date or datetime.now().strftime("%Y-%m-%d")

            report = self._report_service.generate_report(all_results, analysis_date)
            SlackNotifier.notify_comprehensive_report(report)

            # 리포트 결과로 반환값 업데이트
            result["recommended_count"] = report.get("candidate_count", 0)

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


def main():
    logger.info("=" * 80)
    logger.info("Quantiq Data Engine Started (Hexagonal Architecture)")
    logger.info("=" * 80)

    # 1. API 서버 시작 (별도 스레드)
    api_thread = threading.Thread(target=run_api, daemon=True)
    api_thread.start()

    # 2. MongoDB 연결
    db = MongoDB.get_db()
    if db is None:
        logger.error("Failed to connect to MongoDB")
        return

    # 3. Kafka 연결 대기
    logger.info("Waiting for Kafka to be ready...")
    time.sleep(5)

    # 4. 서비스 초기화
    economic_service = LegacyEconomicServiceAdapter(EconomicDataService())
    slack_notifier = LegacySlackNotifierAdapter()

    # 4.1. PostgreSQL 커넥션 풀 생성 (모든 repository에서 공유)
    pg_pool = PostgreSQL.get_pool()

    # 4.2. 새 Hexagonal 분석 서비스 초기화
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

    # RecommendationApplicationService는 향후 사용 예정
    _recommendation_app_service = RecommendationApplicationService(
        technical_service=technical_app_service,
        sentiment_service=None,  # TODO: 감정 분석 서비스 연결
        notifier=analysis_notifier
    )

    # 4.3. 새 서비스 어댑터 (핸들러 프로토콜 호환)
    technical_service = NewTechnicalAnalysisAdapter(technical_app_service)

    # 4.3.1. REST API에 분석 서비스 등록
    analysis_router.set_technical_service(technical_service)

    # 4.4. 레거시 서비스 (점진적 교체 예정)
    recommendation_service = RecommendationService()
    sentiment_service = LegacySentimentAnalysisAdapter(recommendation_service)

    # 5. Kafka Producer 초기화
    kafka_producer = KafkaProducerAdapter(settings)

    # 6. 핸들러 생성
    economic_handler = EconomicDataHandler(
        service=economic_service,
        notifier=slack_notifier,
        publisher=kafka_producer
    )

    technical_handler = TechnicalAnalysisHandler(
        service=technical_service,
        publisher=kafka_producer
    )

    sentiment_handler = SentimentAnalysisHandler(
        service=sentiment_service,
        publisher=kafka_producer
    )

    # 6.1. 백테스트 핸들러 (SCRUM-186)
    strategy_pg_repository = PostgresStrategyRepository(pool=pg_pool)
    backtest_repository = PostgresBacktestRepository(pool=pg_pool)

    backtest_service = BacktestApplicationService(
        strategy_repository=strategy_pg_repository
    )

    backtest_handler = BacktestRequestHandler(
        backtest_service=backtest_service,
        backtest_repository=backtest_repository,
        publisher=kafka_producer
    )

    # 6.2. Vertex AI 핸들러 (GCP 활성화 시에만)
    vertexai_handler = None
    prediction_service = None

    if settings.gcp.enabled:
        logger.info("GCP enabled - initializing Vertex AI services...")
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

            # ML Router에 서비스 설정
            ml_router.set_prediction_service(prediction_service)

            # Vertex AI Kafka 핸들러
            vertexai_handler = VertexAIHandler(
                service=prediction_service,
                publisher=kafka_producer
            )
            logger.info("✅ Vertex AI services initialized")
        except Exception as e:
            logger.error(f"❌ Failed to initialize Vertex AI services: {e}")
    else:
        logger.info("GCP disabled - Vertex AI services not available")

    # 7. Kafka Consumer 설정
    consumer = KafkaConsumerAdapter(
        settings=settings,
        group_id="quantiq-data-engine-hex"
    )

    # 핸들러 등록
    consumer.register_handler(economic_handler.topic, economic_handler.handle)
    consumer.register_handler(technical_handler.topic, technical_handler.handle)
    consumer.register_handler(sentiment_handler.topic, sentiment_handler.handle)
    consumer.register_handler(backtest_handler.topic, backtest_handler.handle)

    # Vertex AI 핸들러 등록 (GCP 활성화 시)
    if vertexai_handler:
        consumer.register_handler(vertexai_handler.topic, vertexai_handler.handle)

    # 토픽 목록
    topics = [
        economic_handler.topic,
        technical_handler.topic,
        sentiment_handler.topic,
        backtest_handler.topic,
    ]

    # Vertex AI 토픽 추가 (GCP 활성화 시)
    if vertexai_handler:
        topics.append(vertexai_handler.topic)

    logger.info(f"Subscribing to topics: {topics}")

    try:
        # 8. Consumer 시작
        consumer.start(topics)

        # 9. 메시지 처리 루프
        logger.info("Starting message processing loop...")

        while True:
            message = consumer.poll_once(timeout=1.0)
            if message:
                try:
                    consumer.process_message(message)
                except Exception as e:
                    logger.exception(f"Error processing message: {e}")

    except KeyboardInterrupt:
        logger.info("Shutdown requested...")
    finally:
        consumer.stop()
        kafka_producer.close()
        PostgreSQL.close_pool()
        logger.info("Data Engine stopped")


if __name__ == "__main__":
    main()
