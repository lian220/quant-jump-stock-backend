package com.quantjumpstock.core.adapter.output.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import com.google.cloud.spring.pubsub.core.PubSubTemplate
import org.springframework.stereotype.Component

/**
 * Analysis Request Publisher Adapter (Output Adapter)
 * 분석 요청 메시지를 Pub/Sub으로 발행합니다.
 */
@Component
class AnalysisRequestPublisherAdapter(
    private val pubSubTemplate: PubSubTemplate,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val TOPIC = "quantiq.analysis.request"

    fun sendRequest(type: String) {
        val payload = mapOf("type" to type, "timestamp" to System.currentTimeMillis())
        val message = objectMapper.writeValueAsString(payload)

        logger.info("Sending analysis request: $message")
        val pubsubTopic = TOPIC.replace('.', '-')
        pubSubTemplate.publish(pubsubTopic, message)
    }
}
