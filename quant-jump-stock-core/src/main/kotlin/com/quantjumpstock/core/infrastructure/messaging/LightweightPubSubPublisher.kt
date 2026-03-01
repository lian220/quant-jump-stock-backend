package com.quantjumpstock.core.infrastructure.messaging

import com.google.api.core.ApiFuture
import com.google.api.gax.core.NoCredentialsProvider
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.FixedTransportChannelProvider
import com.google.cloud.pubsub.v1.Publisher
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.TopicName
import io.grpc.ManagedChannelBuilder
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 경량 Pub/Sub 퍼블리셔
 *
 * Spring Cloud GCP PubSubTemplate 대신 google-cloud-pubsub Publisher API를 직접 사용합니다.
 * - gRPC/Netty 부트스트랩 오버헤드 감소 (Publisher는 lazy 초기화)
 * - PUBSUB_EMULATOR_HOST 환경변수 자동 감지
 * - 프로덕션에서는 ADC(Application Default Credentials) 사용
 */
@Component
class LightweightPubSubPublisher(
    @Value("\${gcp.project-id:quantiq-local}")
    private val projectId: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val publisherCache = ConcurrentHashMap<String, Publisher>()
    private val emulatorHost: String? = System.getenv("PUBSUB_EMULATOR_HOST")

    /**
     * 메시지를 Pub/Sub 토픽에 발행합니다.
     * @param topic 하이픈 구분 토픽명 (예: quantiq-analysis-completed)
     * @param message JSON 문자열
     * @return 발행 결과 Future (messageId)
     */
    fun publish(topic: String, message: String): ApiFuture<String> {
        val publisher = getOrCreatePublisher(topic)
        val pubsubMessage = PubsubMessage.newBuilder()
            .setData(ByteString.copyFromUtf8(message))
            .build()
        return publisher.publish(pubsubMessage)
    }

    private fun getOrCreatePublisher(topic: String): Publisher {
        return publisherCache.computeIfAbsent(topic) { createPublisher(it) }
    }

    private fun createPublisher(topic: String): Publisher {
        val topicName = TopicName.of(projectId, topic)
        val builder = Publisher.newBuilder(topicName)

        if (!emulatorHost.isNullOrBlank()) {
            logger.info("Pub/Sub 에뮬레이터 사용: $emulatorHost (topic=$topic)")
            val channel = ManagedChannelBuilder.forTarget(emulatorHost)
                .usePlaintext()
                .build()
            builder.setChannelProvider(
                FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel))
            )
            builder.setCredentialsProvider(NoCredentialsProvider.create())
        }

        return builder.build().also {
            logger.info("Publisher 생성: topic=$topic, projectId=$projectId")
        }
    }

    @PreDestroy
    fun shutdown() {
        publisherCache.values.forEach { publisher ->
            try {
                publisher.shutdown()
                publisher.awaitTermination(5, TimeUnit.SECONDS)
            } catch (e: Exception) {
                logger.warn("Publisher 종료 실패", e)
            }
        }
    }
}
