package com.quantjumpstock.core.adapter.output.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/**
 * Analysis Request Publisher Adapter (Output Adapter)
 * 분석 요청 메시지를 Kafka로 발행합니다.
 */
@Component
class AnalysisRequestPublisherAdapter(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val TOPIC = "quantiq.analysis.request"

    fun sendRequest(type: String) {
        val payload = mapOf("type" to type, "timestamp" to System.currentTimeMillis())
        val message = objectMapper.writeValueAsString(payload)

        logger.info("Sending analysis request: $message")
        kafkaTemplate.send(TOPIC, message)
    }
}
