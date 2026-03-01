package com.quantjumpstock.core.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.quantjumpstock.core.infrastructure.messaging.LightweightPubSubPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Generic Event Publisher
 * Pub/Sub으로 이벤트를 발행하는 범용 서비스입니다.
 */
@Service
class EventPublisher(
    private val publisher: LightweightPubSubPublisher,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 이벤트를 Pub/Sub 토픽에 발행합니다.
     *
     * @param topic 토픽명 (dot 표기법, 자동 변환됨)
     * @param event 발행할 이벤트 (BaseEvent)
     */
    fun publish(topic: String, event: BaseEvent) {
        try {
            val message = objectMapper.writeValueAsString(event)
            val pubsubTopic = toPubSubTopic(topic)
            logger.info("📤 Publishing event to topic [$pubsubTopic]: eventId=${event.eventId}, type=${event.eventType}")
            logger.debug("Event payload: $message")

            publisher.publish(pubsubTopic, message)
                .addListener(
                    { logger.info("✅ Event published successfully: ${event.eventId}") },
                    { command -> command.run() }
                )
        } catch (e: Exception) {
            logger.error("❌ Error publishing event to topic [$topic]", e)
            throw e
        }
    }

    /**
     * 이벤트를 비동기로 발행하고 결과를 반환하지 않습니다.
     */
    fun publishAsync(topic: String, event: BaseEvent) {
        try {
            val message = objectMapper.writeValueAsString(event)
            val pubsubTopic = toPubSubTopic(topic)
            logger.info("📤 Publishing event (async) to topic [$pubsubTopic]: ${event.eventType}")
            publisher.publish(pubsubTopic, message)
                .addListener(
                    { logger.debug("Async publish completed for topic [$pubsubTopic]") },
                    { command -> command.run() }
                )
        } catch (e: Exception) {
            logger.error("❌ Error publishing event asynchronously to topic [$topic]", e)
        }
    }

    companion object {
        fun toPubSubTopic(topic: String): String {
            return topic.replace('.', '-')
        }
    }
}

/**
 * Stock Event Publisher
 * 주식 관련 이벤트를 발행합니다.
 */
@Service
class StockEventPublisher(
    private val eventPublisher: EventPublisher
) {
    fun publishPriceUpdated(payload: StockPriceUpdatedPayload) {
        val event = BaseEvent(
            eventType = EventTopics.STOCK_PRICE_UPDATED,
            payload = payload
        )
        eventPublisher.publish(EventTopics.STOCK_PRICE_UPDATED, event)
    }

    fun publishDataSyncRequested(payload: StockDataSyncRequestedPayload) {
        val event = BaseEvent(
            eventType = EventTopics.STOCK_DATA_SYNC_REQUESTED,
            payload = payload
        )
        eventPublisher.publish(EventTopics.STOCK_DATA_SYNC_REQUESTED, event)
    }

    fun publishDataRefreshed(payload: StockDataRefreshedPayload) {
        val event = BaseEvent(
            eventType = EventTopics.STOCK_DATA_REFRESHED,
            payload = payload
        )
        eventPublisher.publish(EventTopics.STOCK_DATA_REFRESHED, event)
    }
}

/**
 * Trading Event Publisher
 * 거래 관련 이벤트를 발행합니다.
 */
@Service
class TradingEventPublisher(
    private val eventPublisher: EventPublisher
) {
    fun publishOrderCreated(payload: TradingOrderCreatedPayload) {
        val event = BaseEvent(
            eventType = EventTopics.TRADING_ORDER_CREATED,
            payload = payload
        )
        eventPublisher.publish(EventTopics.TRADING_ORDER_CREATED, event)
    }

    fun publishOrderExecuted(payload: TradingOrderExecutedPayload) {
        val event = BaseEvent(
            eventType = EventTopics.TRADING_ORDER_EXECUTED,
            payload = payload
        )
        eventPublisher.publish(EventTopics.TRADING_ORDER_EXECUTED, event)
    }

    fun publishSignalDetected(payload: TradingSignalDetectedPayload) {
        val event = BaseEvent(
            eventType = EventTopics.TRADING_SIGNAL_DETECTED,
            payload = payload
        )
        eventPublisher.publish(EventTopics.TRADING_SIGNAL_DETECTED, event)
    }

    fun publishBalanceUpdated(payload: TradingBalanceUpdatedPayload) {
        val event = BaseEvent(
            eventType = EventTopics.TRADING_BALANCE_UPDATED,
            payload = payload
        )
        eventPublisher.publish(EventTopics.TRADING_BALANCE_UPDATED, event)
    }
}

/**
 * Analysis Event Publisher
 * 분석 관련 이벤트를 발행합니다.
 */
@Service
class AnalysisEventPublisher(
    private val eventPublisher: EventPublisher
) {
    fun publishAnalysisRequest(payload: AnalysisRequestPayload) {
        val event = BaseEvent(
            eventType = EventTopics.ANALYSIS_REQUEST,
            payload = payload
        )
        eventPublisher.publish(EventTopics.ANALYSIS_REQUEST, event)
    }

    fun publishAnalysisCompleted(payload: AnalysisCompletedPayload) {
        val event = BaseEvent(
            eventType = EventTopics.ANALYSIS_COMPLETED,
            payload = payload
        )
        eventPublisher.publish(EventTopics.ANALYSIS_COMPLETED, event)
    }

    fun publishRecommendationGenerated(payload: AnalysisRecommendationGeneratedPayload) {
        val event = BaseEvent(
            eventType = EventTopics.ANALYSIS_RECOMMENDATION_GENERATED,
            payload = payload
        )
        eventPublisher.publish(EventTopics.ANALYSIS_RECOMMENDATION_GENERATED, event)
    }

    fun publishPredictionCompleted(payload: AnalysisPredictionCompletedPayload) {
        val event = BaseEvent(
            eventType = EventTopics.ANALYSIS_PREDICTION_COMPLETED,
            payload = payload
        )
        eventPublisher.publish(EventTopics.ANALYSIS_PREDICTION_COMPLETED, event)
    }
}

/**
 * Economic Event Publisher
 * 경제 데이터 관련 이벤트를 발행합니다.
 */
@Service
class EconomicEventPublisher(
    private val eventPublisher: EventPublisher
) {
    fun publishDataSyncRequested(payload: EconomicDataSyncRequestedPayload) {
        val event = BaseEvent(
            eventType = EventTopics.ECONOMIC_DATA_SYNC_REQUESTED,
            payload = payload
        )
        eventPublisher.publish(EventTopics.ECONOMIC_DATA_SYNC_REQUESTED, event)
    }

    fun publishDataUpdated(payload: EconomicDataUpdatedPayload) {
        val event = BaseEvent(
            eventType = EventTopics.ECONOMIC_DATA_UPDATED,
            payload = payload
        )
        eventPublisher.publish(EventTopics.ECONOMIC_DATA_UPDATED, event)
    }

    fun publishDataSyncFailed(payload: EconomicDataSyncFailedPayload) {
        val event = BaseEvent(
            eventType = EventTopics.ECONOMIC_DATA_SYNC_FAILED,
            payload = payload
        )
        eventPublisher.publish(EventTopics.ECONOMIC_DATA_SYNC_FAILED, event)
    }
}
