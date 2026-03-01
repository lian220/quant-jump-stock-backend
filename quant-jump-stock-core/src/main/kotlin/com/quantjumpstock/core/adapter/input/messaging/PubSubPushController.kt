package com.quantjumpstock.core.adapter.input.messaging

import com.quantjumpstock.core.application.messaging.PubSubMessageHandlerService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.Base64

/**
 * Pub/Sub Push 엔드포인트 (Input Adapter)
 *
 * Cloud Run에서 Pub/Sub Push 구독을 수신합니다.
 * Data Engine이 이미 사용하는 패턴(/_ah/push-handler/{topic})을 따릅니다.
 *
 * 보안: Cloud Run에서는 --no-allow-unauthenticated + Pub/Sub invoker SA OIDC 토큰으로 보호.
 *       VM에서는 SecurityConfig에서 내부 전용으로 제한.
 */
@RestController
@RequestMapping("/_ah/push-handler")
class PubSubPushController(
    private val handlerService: PubSubMessageHandlerService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Pub/Sub Push 메시지 수신
     *
     * @param topicName 하이픈 구분 토픽명 (예: quantiq-analysis-completed)
     * @param envelope Pub/Sub Push 메시지 봉투
     */
    @PostMapping("/{topicName}")
    fun handlePush(
        @PathVariable topicName: String,
        @RequestBody envelope: PubSubPushEnvelope
    ): ResponseEntity<String> {
        val dotTopic = topicName.replace('-', '.')
        logger.info("📥 Pub/Sub Push 수신: topic=$dotTopic, messageId=${envelope.message.messageId}")

        return try {
            val data = String(Base64.getDecoder().decode(envelope.message.data))
            handlerService.handleMessage(dotTopic, data)
            ResponseEntity.ok("ok")
        } catch (e: Exception) {
            logger.error("❌ Pub/Sub Push 처리 실패: topic=$dotTopic", e)
            // 200 반환하여 재전송 방지 (실패해도 ACK)
            ResponseEntity.ok("error: ${e.message}")
        }
    }
}

/**
 * Pub/Sub Push 메시지 봉투
 * https://cloud.google.com/pubsub/docs/push#receive_push
 */
data class PubSubPushEnvelope(
    val message: PubSubPushMessage,
    val subscription: String = ""
)

data class PubSubPushMessage(
    val data: String = "",
    val messageId: String = "",
    val attributes: Map<String, String> = emptyMap(),
    val publishTime: String = ""
)
