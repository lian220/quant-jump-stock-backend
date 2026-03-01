package com.quantjumpstock.core.adapter.input.messaging

import com.quantjumpstock.core.application.messaging.PubSubMessageHandlerService
import com.quantjumpstock.core.events.EventTopics
import com.google.cloud.spring.pubsub.core.PubSubTemplate
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Pub/Sub Event Listener Adapter (Input Adapter) — Pull 구독 방식
 *
 * quantiq-data-engine에서 발행된 이벤트를 수신하여 처리합니다.
 * messaging.provider=pubsub 일 때 활성화됩니다.
 *
 * 핸들러 로직은 PubSubMessageHandlerService로 위임합니다.
 * Cloud Run 전환 후 Push 방식(PubSubPushController)으로 대체 예정.
 */
@Component
@ConditionalOnProperty(name = ["pubsub.pull.enabled"], havingValue = "true", matchIfMissing = true)
class PubSubEventListenerAdapter(
    private val pubSubTemplate: PubSubTemplate,
    private val handlerService: PubSubMessageHandlerService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun subscribeAll() {
        Thread.startVirtualThread {
            try {
                subscribe(EventTopics.ANALYSIS_COMPLETED, handlerService::handleAnalysisCompleted)
                subscribe(EventTopics.ECONOMIC_DATA_UPDATED, handlerService::handleEconomicDataUpdated)
                subscribe(EventTopics.ECONOMIC_DATA_SYNC_FAILED, handlerService::handleEconomicDataSyncFailed)
                subscribe(EventTopics.TRADING_SIGNAL_DETECTED, handlerService::handleTradingSignalDetected)
                subscribe(EventTopics.BACKTEST_COMPLETED, handlerService::handleBacktestCompleted)
                subscribe(EventTopics.BACKTEST_FAILED, handlerService::handleBacktestFailed)
                logger.info("✅ Pub/Sub Pull 구독 초기화 완료 (6개 토픽)")
            } catch (e: Exception) {
                logger.error("❌ Pub/Sub Pull 구독 초기화 실패", e)
            }
        }
    }

    private fun subscribe(topic: String, handler: (String) -> Unit) {
        val subscription = toPubSubSubscription(topic)
        pubSubTemplate.subscribe(subscription) { message: BasicAcknowledgeablePubsubMessage ->
            val data = message.pubsubMessage.data.toStringUtf8()
            try {
                handler(data)
                message.ack()
            } catch (e: Exception) {
                logger.error("메시지 처리 실패 (subscription=$subscription, ACK처리 재시도 안함)", e)
                message.ack()
            }
        }
        logger.info("Pub/Sub 구독 시작: $subscription")
    }

    companion object {
        fun toPubSubSubscription(topic: String): String {
            return topic.replace('.', '-') + "-sub"
        }
    }
}
