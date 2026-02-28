package com.quantjumpstock.core.adapter.output.messaging

import com.quantjumpstock.core.domain.economic.port.output.MessagePublisher
import com.quantjumpstock.core.domain.model.AnalysisRequest
import com.quantjumpstock.core.domain.model.BacktestRequest
import com.quantjumpstock.core.domain.model.EconomicDataUpdateRequest
import com.quantjumpstock.core.domain.model.VertexAIPredictionRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.cloud.spring.pubsub.core.PubSubTemplate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Pub/Sub Message Publisher Adapter (Output Adapter)
 * MessagePublisher 인터페이스를 구현하여 Google Cloud Pub/Sub과 연동합니다.
 * 로컬: Pub/Sub 에뮬레이터, 프로덕션: 실제 GCP Pub/Sub 사용.
 *
 * 발행 결과를 동기적으로 기다려 실패 시 예외를 호출자에게 전파합니다.
 */
@Component
class PubSubMessagePublisherAdapter(
    private val pubSubTemplate: PubSubTemplate,
    private val objectMapper: ObjectMapper
) : MessagePublisher {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun publishEconomicDataUpdateRequest(
        topic: String,
        request: EconomicDataUpdateRequest
    ) {
        val event = mapOf(
            "eventId" to UUID.randomUUID().toString(),
            "eventType" to topic,
            "version" to "1.0",
            "timestamp" to request.timestamp,
            "source" to request.source,
            "payload" to mapOf(
                "requestId" to request.requestId,
                "source" to request.source,
                "timestamp" to request.timestamp,
                "threadTs" to request.threadTs,
                "startDate" to request.startDate,
                "endDate" to request.endDate
            )
        )
        publishAndAwait(toPubSubTopic(topic), event, request.requestId)
    }

    override fun publishAnalysisRequest(
        topic: String,
        request: AnalysisRequest
    ) {
        val event = mapOf(
            "eventId" to UUID.randomUUID().toString(),
            "eventType" to topic,
            "version" to "1.0",
            "timestamp" to request.timestamp,
            "source" to request.source,
            "payload" to mapOf(
                "requestId" to request.requestId,
                "source" to request.source,
                "timestamp" to request.timestamp,
                "threadTs" to request.threadTs,
                "analysisType" to request.analysisType,
                "startDate" to request.startDate,
                "endDate" to request.endDate
            )
        )
        publishAndAwait(toPubSubTopic(topic), event, request.requestId)
    }

    override fun publishVertexAIPredictionRequest(
        topic: String,
        request: VertexAIPredictionRequest
    ) {
        val event = mapOf(
            "eventId" to UUID.randomUUID().toString(),
            "eventType" to topic,
            "version" to "1.0",
            "timestamp" to request.timestamp,
            "source" to request.source,
            "payload" to mapOf(
                "requestId" to request.requestId,
                "source" to request.source,
                "timestamp" to request.timestamp,
                "threadTs" to request.threadTs,
                "envVars" to request.envVars
            )
        )
        publishAndAwait(toPubSubTopic(topic), event, request.requestId)
    }

    override fun publishBacktestRequest(
        topic: String,
        request: BacktestRequest
    ) {
        val payload = mutableMapOf<String, Any?>(
            "requestId" to request.requestId,
            "strategyId" to request.strategyId,
            "startDate" to request.startDate,
            "endDate" to request.endDate,
            "initialCapital" to request.initialCapital,
            "userId" to request.userId,
            "tickers" to request.tickers,
            "benchmark" to request.benchmark,
            "benchmarks" to request.benchmarks,
            "rebalancePeriod" to request.rebalancePeriod,
            "universeType" to request.universeType,
            "backtestType" to request.backtestType,
            "forceFull" to request.forceFull
        )
        request.riskSettings?.let { payload["riskSettings"] = it }
        request.positionSizing?.let { payload["positionSizing"] = it }
        request.tradingCosts?.let { payload["tradingCosts"] = it }

        val event = mapOf(
            "eventId" to UUID.randomUUID().toString(),
            "eventType" to topic,
            "version" to "1.0",
            "timestamp" to request.timestamp,
            "source" to request.source,
            "payload" to payload
        )
        publishAndAwait(toPubSubTopic(topic), event, request.requestId)
    }

    /**
     * Pub/Sub 메시지를 발행하고 결과를 동기적으로 기다립니다.
     * 발행 실패 시 예외를 던져 호출자(스케줄러 Job 등)가 실패를 감지할 수 있도록 합니다.
     */
    private fun publishAndAwait(pubsubTopic: String, event: Map<String, Any?>, requestId: String) {
        val eventJson = objectMapper.writeValueAsString(event)
        logger.debug("📤 Pub/Sub 메시지 생성: topic={}, requestId={}", pubsubTopic, requestId)

        try {
            pubSubTemplate.publish(pubsubTopic, eventJson)
                .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            logger.info("✅ Pub/Sub 메시지 발행 성공: topic={}, requestId={}", pubsubTopic, requestId)
        } catch (e: Exception) {
            logger.error("❌ Pub/Sub 메시지 발행 실패: topic={}, requestId={}", pubsubTopic, requestId, e)
            throw RuntimeException("Pub/Sub 메시지 발행 실패: topic=$pubsubTopic", e)
        }
    }

    companion object {
        private const val PUBLISH_TIMEOUT_SECONDS = 30L

        fun toPubSubTopic(topic: String): String {
            return topic.replace('.', '-')
        }
    }
}
