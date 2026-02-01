package com.quantjumpstock.core.adapter.output.messaging

import com.quantjumpstock.core.domain.economic.port.output.MessagePublisher
import com.quantjumpstock.core.domain.model.AnalysisRequest
import com.quantjumpstock.core.domain.model.EconomicDataUpdateRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.*

/**
 * Kafka Message Publisher Adapter (Output Adapter)
 * MessagePublisher 인터페이스를 구현하여 Kafka와 연동합니다.
 */
@Component
class KafkaMessagePublisherAdapter(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) : MessagePublisher {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun publishEconomicDataUpdateRequest(
        topic: String,
        request: EconomicDataUpdateRequest
    ) {
        try {
            // 이벤트 래퍼 생성 (eventType 포함)
            val event = mapOf(
                "eventId" to UUID.randomUUID().toString(),
                "eventType" to topic,  // 토픽을 eventType으로 사용
                "version" to "1.0",
                "timestamp" to request.timestamp,
                "source" to request.source,
                "payload" to mapOf(
                    "requestId" to request.requestId,
                    "source" to request.source,
                    "timestamp" to request.timestamp,
                    "threadTs" to request.threadTs  // Slack 스레드 타임스탬프 추가
                )
            )

            val eventJson = objectMapper.writeValueAsString(event)

            logger.debug("📤 Kafka 메시지 생성: $eventJson")

            kafkaTemplate.send(
                topic,
                request.requestId,
                eventJson
            )
            logger.info("Kafka 메시지 발행 성공: topic=$topic, requestId=${request.requestId}")
        } catch (e: Exception) {
            logger.error("Kafka 메시지 발행 실패: topic=$topic", e)
            throw e
        }
    }

    override fun publishAnalysisRequest(
        topic: String,
        request: AnalysisRequest
    ) {
        try {
            // 이벤트 래퍼 생성 (eventType 포함)
            val event = mapOf(
                "eventId" to UUID.randomUUID().toString(),
                "eventType" to topic,  // 토픽을 eventType으로 사용
                "version" to "1.0",
                "timestamp" to request.timestamp,
                "source" to request.source,
                "payload" to mapOf(
                    "requestId" to request.requestId,
                    "source" to request.source,
                    "timestamp" to request.timestamp,
                    "threadTs" to request.threadTs,  // Slack 스레드 타임스탬프 추가
                    "analysisType" to request.analysisType,
                    "targetDate" to request.targetDate  // 분석 대상 날짜 추가
                )
            )

            val eventJson = objectMapper.writeValueAsString(event)

            logger.debug("📤 Kafka 메시지 생성: $eventJson")

            kafkaTemplate.send(
                topic,
                request.requestId,
                eventJson
            )
            logger.info("Kafka 메시지 발행 성공: topic=$topic, requestId=${request.requestId}, type=${request.analysisType}")
        } catch (e: Exception) {
            logger.error("Kafka 메시지 발행 실패: topic=$topic", e)
            throw e
        }
    }
}
