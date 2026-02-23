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

/**
 * Pub/Sub Message Publisher Adapter (Output Adapter)
 * MessagePublisher 인터페이스를 구현하여 Google Cloud Pub/Sub과 연동합니다.
 * 로컬: Pub/Sub 에뮬레이터, 프로덕션: 실제 GCP Pub/Sub 사용.
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
        try {
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

            val eventJson = objectMapper.writeValueAsString(event)
            val pubsubTopic = toPubSubTopic(topic)

            logger.debug("📤 Pub/Sub 메시지 생성: $eventJson")

            pubSubTemplate.publish(pubsubTopic, eventJson)
                .whenComplete { _, ex ->
                    if (ex == null) {
                        logger.info("Pub/Sub 메시지 발행 성공: topic=$pubsubTopic, requestId=${request.requestId}")
                    } else {
                        logger.error("Pub/Sub 메시지 발행 실패: topic=$pubsubTopic", ex)
                    }
                }
        } catch (e: Exception) {
            logger.error("Pub/Sub 메시지 발행 실패: topic=$topic", e)
            throw e
        }
    }

    override fun publishAnalysisRequest(
        topic: String,
        request: AnalysisRequest
    ) {
        try {
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

            val eventJson = objectMapper.writeValueAsString(event)
            val pubsubTopic = toPubSubTopic(topic)

            logger.debug("📤 Pub/Sub 메시지 생성: $eventJson")

            pubSubTemplate.publish(pubsubTopic, eventJson)
                .whenComplete { _, ex ->
                    if (ex == null) {
                        logger.info("Pub/Sub 메시지 발행 성공: topic=$pubsubTopic, requestId=${request.requestId}, type=${request.analysisType}")
                    } else {
                        logger.error("Pub/Sub 메시지 발행 실패: topic=$pubsubTopic", ex)
                    }
                }
        } catch (e: Exception) {
            logger.error("Pub/Sub 메시지 발행 실패: topic=$topic", e)
            throw e
        }
    }

    override fun publishVertexAIPredictionRequest(
        topic: String,
        request: VertexAIPredictionRequest
    ) {
        try {
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

            val eventJson = objectMapper.writeValueAsString(event)
            val pubsubTopic = toPubSubTopic(topic)

            logger.debug("📤 Pub/Sub Vertex AI 메시지 생성: $eventJson")

            pubSubTemplate.publish(pubsubTopic, eventJson)
                .whenComplete { _, ex ->
                    if (ex == null) {
                        logger.info("Pub/Sub Vertex AI 메시지 발행 성공: topic=$pubsubTopic, requestId=${request.requestId}")
                    } else {
                        logger.error("Pub/Sub Vertex AI 메시지 발행 실패: topic=$pubsubTopic", ex)
                    }
                }
        } catch (e: Exception) {
            logger.error("Pub/Sub Vertex AI 메시지 발행 실패: topic=$topic", e)
            throw e
        }
    }

    override fun publishBacktestRequest(
        topic: String,
        request: BacktestRequest
    ) {
        try {
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

            val eventJson = objectMapper.writeValueAsString(event)
            val pubsubTopic = toPubSubTopic(topic)

            logger.debug("📤 Pub/Sub 백테스트 요청 메시지 생성: $eventJson")

            pubSubTemplate.publish(pubsubTopic, eventJson)
                .whenComplete { _, ex ->
                    if (ex == null) {
                        logger.info("Pub/Sub 백테스트 요청 메시지 발행 성공: topic=$pubsubTopic, requestId=${request.requestId}, strategyId=${request.strategyId}")
                    } else {
                        logger.error("Pub/Sub 백테스트 요청 메시지 발행 실패: topic=$pubsubTopic", ex)
                    }
                }
        } catch (e: Exception) {
            logger.error("Pub/Sub 백테스트 요청 메시지 발행 실패: topic=$topic", e)
            throw e
        }
    }

    companion object {
        /**
         * dot 표기법 토픽을 Pub/Sub 토픽명으로 변환
         * "economic.data.update.request" → "economic-data-update-request"
         */
        fun toPubSubTopic(topic: String): String {
            return topic.replace('.', '-')
        }
    }
}
