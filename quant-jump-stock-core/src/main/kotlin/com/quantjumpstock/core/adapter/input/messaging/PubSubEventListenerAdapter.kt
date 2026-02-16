package com.quantjumpstock.core.adapter.input.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.quantjumpstock.core.events.EventTopics
import com.quantjumpstock.core.application.trading.AutoTradingService
import com.quantjumpstock.core.application.backtest.BacktestResultSaveService
import com.quantjumpstock.core.domain.news.port.input.NewsCollectionUseCase
import com.google.cloud.spring.pubsub.core.PubSubTemplate
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Pub/Sub Event Listener Adapter (Input Adapter)
 * quantiq-data-engine에서 발행된 이벤트를 수신하여 처리합니다.
 * messaging.provider=pubsub 일 때 활성화됩니다.
 * Kafka는 완전 제거되었으며, 모든 환경에서 Pub/Sub을 사용합니다.
 */
@Component
@ConditionalOnProperty(name = ["messaging.provider"], havingValue = "pubsub", matchIfMissing = true)
class PubSubEventListenerAdapter(
    private val pubSubTemplate: PubSubTemplate,
    private val objectMapper: ObjectMapper,
    private val autoTradingService: AutoTradingService,
    private val backtestResultSaveService: BacktestResultSaveService,
    private val newsCollectionUseCase: NewsCollectionUseCase
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun subscribeAll() {
        subscribe(EventTopics.ANALYSIS_COMPLETED, ::handleAnalysisCompleted)
        subscribe(EventTopics.ECONOMIC_DATA_UPDATED, ::handleEconomicDataUpdated)
        subscribe(EventTopics.ECONOMIC_DATA_SYNC_FAILED, ::handleEconomicDataSyncFailed)
        subscribe(EventTopics.TRADING_SIGNAL_DETECTED, ::handleTradingSignalDetected)
        subscribe(EventTopics.BACKTEST_COMPLETED, ::handleBacktestCompleted)
        subscribe(EventTopics.BACKTEST_FAILED, ::handleBacktestFailed)
        subscribe(EventTopics.NEWS_COLLECTED, ::handleNewsCollected)
        subscribe(EventTopics.NEWS_COLLECTION_FAILED, ::handleNewsCollectionFailed)
    }

    private fun subscribe(topic: String, handler: (String) -> Unit) {
        val subscription = toPubSubSubscription(topic)
        pubSubTemplate.subscribe(subscription) { message: BasicAcknowledgeablePubsubMessage ->
            val data = message.pubsubMessage.data.toStringUtf8()
            try {
                handler(data)
                message.ack()
            } catch (e: Exception) {
                logger.error("메시지 처리 실패 (subscription=$subscription)", e)
                message.nack()
            }
        }
        logger.info("Pub/Sub 구독 시작: $subscription")
    }

    private fun handleAnalysisCompleted(message: String) {
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

            logger.info("🤖 자동 매매 로직 실행 중...")
            autoTradingService.executeAutoTrading()

            logger.info("✅ 분석 완료 이벤트 처리 완료")
        } catch (e: Exception) {
            logger.error("❌ 분석 완료 이벤트 처리 실패: $message", e)
            throw e
        }
    }

    private fun handleEconomicDataUpdated(message: String) {
        logger.info("=".repeat(80))
        logger.info("📥 경제 데이터 업데이트 완료 이벤트 수신")
        logger.info("=".repeat(80))
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val payload = event.get("payload")
                ?: throw IllegalArgumentException("경제 데이터 업데이트 이벤트에 payload가 없습니다: $message")
            val requestId = payload.get("requestId")?.asText() ?: "unknown"
            val status = payload.get("status")?.asText() ?: "unknown"
            val duration = payload.get("duration")?.asDouble() ?: 0.0

            logger.info("✅ 경제 데이터 업데이트 완료")
            logger.info("Request ID: $requestId")
            logger.info("Status: $status")
            logger.info("Duration: ${duration}초")
        } catch (e: Exception) {
            logger.error("❌ 경제 데이터 업데이트 이벤트 처리 실패: $message", e)
            throw e
        }
    }

    private fun handleEconomicDataSyncFailed(message: String) {
        logger.warn("=".repeat(80))
        logger.warn("⚠️ 경제 데이터 동기화 실패 이벤트 수신")
        logger.warn("=".repeat(80))
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val payload = event.get("payload")
                ?: throw IllegalArgumentException("경제 데이터 동기화 실패 이벤트에 payload가 없습니다: $message")
            val requestId = payload.get("requestId")?.asText() ?: "unknown"
            val errorCode = payload.get("errorCode")?.asText() ?: "UNKNOWN"
            val errorMessage = payload.get("errorMessage")?.asText() ?: "Unknown error"
            val retryable = payload.get("retryable")?.asBoolean() ?: false

            logger.warn("❌ 경제 데이터 동기화 실패")
            logger.warn("Request ID: $requestId")
            logger.warn("Error Code: $errorCode")
            logger.warn("Error Message: $errorMessage")
            logger.warn("Retryable: $retryable")

            if (retryable) {
                logger.info("재시도 가능한 오류입니다. 재시도 로직 실행을 고려하세요.")
            }
        } catch (e: Exception) {
            logger.error("❌ 경제 데이터 동기화 실패 이벤트 처리 실패: $message", e)
            throw e
        }
    }

    private fun handleTradingSignalDetected(message: String) {
        logger.info("=".repeat(80))
        logger.info("🔔 매매 신호 감지 이벤트 수신")
        logger.info("=".repeat(80))
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val payload = event.get("payload")
                ?: throw IllegalArgumentException("매매 신호 이벤트에 payload가 없습니다: $message")
            val symbol = payload.get("symbol")?.asText() ?: "unknown"
            val signalType = payload.get("signalType")?.asText() ?: "unknown"
            val confidence = payload.get("confidence")?.asDouble() ?: 0.0

            logger.info("📊 매매 신호")
            logger.info("종목: $symbol")
            logger.info("신호: $signalType")
            logger.info("신뢰도: ${confidence * 100}%")
        } catch (e: Exception) {
            logger.error("❌ 매매 신호 이벤트 처리 실패: $message", e)
            throw e
        }
    }

    private fun handleBacktestCompleted(message: String) {
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

            backtestResultSaveService.saveBacktestResult(payload)

            logger.info("✅ 백테스트 결과 저장 완료")
        } catch (e: Exception) {
            logger.error("❌ 백테스트 완료 이벤트 처리 실패: $message", e)
            throw e
        }
    }

    private fun handleBacktestFailed(message: String) {
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

            backtestResultSaveService.saveBacktestFailure(payload)

            logger.info("✅ 백테스트 실패 결과 저장 완료")
        } catch (e: Exception) {
            logger.error("❌ 백테스트 실패 이벤트 처리 실패: $message", e)
            throw e
        }
    }

    private fun handleNewsCollected(message: String) {
        logger.info("=".repeat(80))
        logger.info("📥 뉴스 수집 완료 이벤트 수신")
        logger.info("=".repeat(80))
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val payload = event.get("payload")
                ?: throw IllegalArgumentException("뉴스 수집 완료 이벤트에 payload가 없습니다: $message")

            val requestId = payload.get("requestId")?.asText() ?: "unknown"
            val source = payload.get("source")?.asText() ?: "SAVETICKER"
            val collectedCount = payload.get("collectedCount")?.asInt() ?: 0
            val articleIds = payload.get("articleIds")
                ?.takeIf { it.isArray }
                ?.map { it.asText() }
                ?: emptyList()

            logger.info("✅ 뉴스 수집 완료: requestId=$requestId, source=$source, ${collectedCount}건")

            if (articleIds.isNotEmpty()) {
                newsCollectionUseCase.processCollectedNews(articleIds, source)
            }
        } catch (e: Exception) {
            logger.error("❌ 뉴스 수집 완료 이벤트 처리 실패: $message", e)
            throw e
        }
    }

    private fun handleNewsCollectionFailed(message: String) {
        logger.warn("=".repeat(80))
        logger.warn("⚠️ 뉴스 수집 실패 이벤트 수신")
        logger.warn("=".repeat(80))
        logger.debug("메시지: $message")

        try {
            val event = objectMapper.readTree(message)
            val payload = event.get("payload")
                ?: throw IllegalArgumentException("뉴스 수집 실패 이벤트에 payload가 없습니다: $message")

            val requestId = payload.get("requestId")?.asText() ?: "unknown"
            val source = payload.get("source")?.asText() ?: "unknown"
            val error = payload.get("error")?.asText() ?: "Unknown error"

            logger.warn("❌ 뉴스 수집 실패: requestId=$requestId, source=$source, error=$error")
        } catch (e: Exception) {
            logger.error("❌ 뉴스 수집 실패 이벤트 처리 실패: $message", e)
            throw e
        }
    }

    companion object {
        /**
         * dot 표기법 토픽을 Pub/Sub 구독명으로 변환
         * "quantiq.analysis.completed" → "quantiq-analysis-completed-sub"
         */
        fun toPubSubSubscription(topic: String): String {
            return topic.replace('.', '-') + "-sub"
        }
    }
}
