"""
Kafka Message Handlers

토픽별 메시지 처리 핸들러.
각 핸들러는 Application 서비스를 호출하여 비즈니스 로직 실행.
"""

import logging
import time
from abc import ABC, abstractmethod
from typing import Optional, Protocol
from datetime import datetime
from pytz import timezone

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
        logger.info("=" * 80)
        logger.info(f"{description} Kafka 메시지 수신")
        logger.info(f"Request ID: {message.request_id}")
        logger.info(f"Target Date: {message.target_date or '당일'}")
        logger.info(f"Thread TS: {message.thread_ts}")
        logger.info("=" * 80)
        return time.time()

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
    def collect_economic_data(self, target_date: Optional[str] = None) -> dict:
        ...


class TechnicalAnalysisServiceProtocol(Protocol):
    """기술적 분석 서비스 프로토콜"""
    def run_technical_analysis(
        self,
        request_id: str,
        thread_ts: Optional[str],
        target_date: Optional[str] = None
    ) -> dict:
        ...


class SentimentAnalysisServiceProtocol(Protocol):
    """감정 분석 서비스 프로토콜"""
    def run_sentiment_analysis(
        self,
        request_id: str,
        thread_ts: Optional[str]
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
            result = self.service.collect_economic_data(target_date=message.target_date)
            elapsed = time.time() - start_time

            self._log_success("경제 데이터 수집", start_time)

            # 성공 알림
            summary = {
                "target_date": result.get("target_date"),
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
                message.target_date
            )

            self._log_success("기술적 분석", start_time)

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
                message.thread_ts
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


class VertexAIPredictionServiceProtocol(Protocol):
    """Vertex AI 예측 서비스 프로토콜"""
    def run_prediction(
        self,
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
            # 메시지에서 환경 변수 추출
            env_vars = message.payload.get("envVars", {})
            thread_ts = message.thread_ts

            # 예측 실행
            result = self.service.run_prediction(
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
