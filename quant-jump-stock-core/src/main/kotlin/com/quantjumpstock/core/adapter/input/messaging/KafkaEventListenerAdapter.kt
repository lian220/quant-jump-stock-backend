package com.quantjumpstock.core.adapter.input.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.quantjumpstock.core.events.EventTopics
import com.quantjumpstock.core.application.trading.AutoTradingService
import com.quantjumpstock.core.application.backtest.BacktestResultSaveService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Kafka Event Listener Adapter (Input Adapter)
 * messaging.provider가 미설정이면 기본값으로 Kafka 사용 (프로덕션 기본)
 */
@Component
@ConditionalOnProperty(name = ["messaging.provider"], havingValue = "kafka", matchIfMissing = true)
class KafkaEventListenerAdapter(
    private val objectMapper: ObjectMapper,
    private val autoTradingService: AutoTradingService,
    private val backtestResultSaveService: BacktestResultSaveService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 분석 완료 이벤트 리스너
     * quantiq.analysis.completed 토픽을 구독합니다.
     */
    @KafkaListener(topics = [EventTopics.ANALYSIS_COMPLETED], groupId = "quantiq-core-group")
    fun listenAnalysisCompleted(message: String) {
        logger.info("=".repeat(80))
        logger.info("📥 분석 완료 이벤트 수신")
        logger.info("=".repeat(80))
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val eventType = event.get("eventType")?.asText()
            val payload = event.get("payload")

            logger.info("Event Type: $eventType")
            logger.info("Payload: $payload")

            // 자동 매매 로직 트리거
            logger.info("🤖 자동 매매 로직 실행 중...")
            autoTradingService.executeAutoTrading()

            logger.info("✅ 분석 완료 이벤트 처리 완료")

        } catch (e: Exception) {
            logger.error("❌ 분석 완료 이벤트 처리 실패: $message", e)
        }
    }

    /**
     * 경제 데이터 업데이트 완료 이벤트 리스너
     * quantiq.economic.data.updated 토픽을 구독합니다.
     */
    @KafkaListener(topics = [EventTopics.ECONOMIC_DATA_UPDATED], groupId = "quantiq-core-group")
    fun listenEconomicDataUpdated(message: String) {
        logger.info("=".repeat(80))
        logger.info("📥 경제 데이터 업데이트 완료 이벤트 수신")
        logger.info("=".repeat(80))
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val payload = event.get("payload")
            val requestId = payload.get("requestId")?.asText() ?: "unknown"
            val status = payload.get("status")?.asText() ?: "unknown"
            val duration = payload.get("duration")?.asDouble() ?: 0.0

            logger.info("✅ 경제 데이터 업데이트 완료")
            logger.info("Request ID: $requestId")
            logger.info("Status: $status")
            logger.info("Duration: ${duration}초")

            // TODO: 후속 처리 로직 (필요시 추가)
            // 예: 경제 데이터 변경에 따른 포트폴리오 재조정

        } catch (e: Exception) {
            logger.error("❌ 경제 데이터 업데이트 이벤트 처리 실패: $message", e)
        }
    }

    /**
     * 경제 데이터 동기화 실패 이벤트 리스너
     * quantiq.economic.data.sync.failed 토픽을 구독합니다.
     */
    @KafkaListener(topics = [EventTopics.ECONOMIC_DATA_SYNC_FAILED], groupId = "quantiq-core-group")
    fun listenEconomicDataSyncFailed(message: String) {
        logger.warn("=".repeat(80))
        logger.warn("⚠️ 경제 데이터 동기화 실패 이벤트 수신")
        logger.warn("=".repeat(80))
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val payload = event.get("payload")
            val requestId = payload.get("requestId")?.asText() ?: "unknown"
            val errorCode = payload.get("errorCode")?.asText() ?: "UNKNOWN"
            val errorMessage = payload.get("errorMessage")?.asText() ?: "Unknown error"
            val retryable = payload.get("retryable")?.asBoolean() ?: false

            logger.warn("❌ 경제 데이터 동기화 실패")
            logger.warn("Request ID: $requestId")
            logger.warn("Error Code: $errorCode")
            logger.warn("Error Message: $errorMessage")
            logger.warn("Retryable: $retryable")

            // TODO: 재시도 로직 (필요시 추가)
            if (retryable) {
                logger.info("재시도 가능한 오류입니다. 재시도 로직 실행을 고려하세요.")
            }

        } catch (e: Exception) {
            logger.error("❌ 경제 데이터 동기화 실패 이벤트 처리 실패: $message", e)
        }
    }

    /**
     * 매매 신호 감지 이벤트 리스너
     * quantiq.trading.signal.detected 토픽을 구독합니다.
     */
    @KafkaListener(topics = [EventTopics.TRADING_SIGNAL_DETECTED], groupId = "quantiq-core-group")
    fun listenTradingSignalDetected(message: String) {
        logger.info("=".repeat(80))
        logger.info("🔔 매매 신호 감지 이벤트 수신")
        logger.info("=".repeat(80))
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val payload = event.get("payload")
            val symbol = payload.get("symbol")?.asText() ?: "unknown"
            val signalType = payload.get("signalType")?.asText() ?: "unknown"
            val confidence = payload.get("confidence")?.asDouble() ?: 0.0

            logger.info("📊 매매 신호")
            logger.info("종목: $symbol")
            logger.info("신호: $signalType")
            logger.info("신뢰도: ${confidence * 100}%")

            // TODO: 매매 신호에 따른 주문 생성 로직
            // autoTradingService.processSignal(symbol, signalType, confidence)

        } catch (e: Exception) {
            logger.error("❌ 매매 신호 이벤트 처리 실패: $message", e)
        }
    }

    // ============================================================================
    // Backtest Events
    // ============================================================================

    /**
     * 백테스트 완료 이벤트 리스너
     * quantiq.backtest.completed 토픽을 구독합니다.
     */
    @KafkaListener(topics = [EventTopics.BACKTEST_COMPLETED], groupId = "quantiq-core-group")
    fun listenBacktestCompleted(message: String) {
        logger.info("=".repeat(80))
        logger.info("📥 백테스트 완료 이벤트 수신")
        logger.info("=".repeat(80))
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val payload = event.get("payload")
                ?: throw IllegalArgumentException("백테스트 완료 이벤트에 payload가 없습니다: $message")

            val requestId = payload.get("requestId")?.asText() ?: "unknown"
            val strategyId = payload.get("strategyId")?.asLong() ?: 0L
            val status = payload.get("status")?.asText() ?: "unknown"

            logger.info("✅ 백테스트 완료")
            logger.info("Request ID: $requestId")
            logger.info("Strategy ID: $strategyId")
            logger.info("Status: $status")

            // 결과 저장
            backtestResultSaveService.saveBacktestResult(payload)

            logger.info("✅ 백테스트 결과 저장 완료")

        } catch (e: Exception) {
            logger.error("❌ 백테스트 완료 이벤트 처리 실패: $message", e)
        }
    }

    /**
     * 백테스트 실패 이벤트 리스너
     * quantiq.backtest.failed 토픽을 구독합니다.
     */
    @KafkaListener(topics = [EventTopics.BACKTEST_FAILED], groupId = "quantiq-core-group")
    fun listenBacktestFailed(message: String) {
        logger.warn("=".repeat(80))
        logger.warn("⚠️ 백테스트 실패 이벤트 수신")
        logger.warn("=".repeat(80))
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val payload = event.get("payload")
                ?: throw IllegalArgumentException("백테스트 실패 이벤트에 payload가 없습니다: $message")

            val requestId = payload.get("requestId")?.asText() ?: "unknown"
            val strategyId = payload.get("strategyId")?.asLong() ?: 0L
            val errorMessage = payload.get("errorMessage")?.asText() ?: "Unknown error"

            logger.warn("❌ 백테스트 실패")
            logger.warn("Request ID: $requestId")
            logger.warn("Strategy ID: $strategyId")
            logger.warn("Error: $errorMessage")

            // 실패 결과 저장
            backtestResultSaveService.saveBacktestFailure(payload)

            logger.info("✅ 백테스트 실패 결과 저장 완료")

        } catch (e: Exception) {
            logger.error("❌ 백테스트 실패 이벤트 처리 실패: $message", e)
        }
    }
}
