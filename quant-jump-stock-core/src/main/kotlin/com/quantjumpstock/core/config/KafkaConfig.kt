package com.quantjumpstock.core.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*

@EnableKafka
@Configuration
class KafkaConfig {

    @Value("\${spring.kafka.bootstrap-servers}") private lateinit var bootstrapServers: String

    // ============================================================================
    // Producer Configuration
    // ============================================================================

    @Bean
    fun producerFactory(): ProducerFactory<String, String> {
        val props = HashMap<String, Any>()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java

        // Performance & Reliability Settings
        props[ProducerConfig.ACKS_CONFIG] = "all" // 모든 replica 확인
        props[ProducerConfig.RETRIES_CONFIG] = 3 // 재시도 3회
        props[ProducerConfig.RETRY_BACKOFF_MS_CONFIG] = 100 // 재시도 간격 100ms

        // Batching for better throughput
        props[ProducerConfig.BATCH_SIZE_CONFIG] = 16384 // 16KB
        props[ProducerConfig.LINGER_MS_CONFIG] = 10 // 10ms 대기 후 배치 전송

        // Compression for network efficiency
        props[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "snappy"

        // Idempotence for exactly-once semantics
        props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true

        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory())
    }

    // ============================================================================
    // Consumer Configuration
    // ============================================================================

    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> {
        val props = HashMap<String, Any>()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ConsumerConfig.GROUP_ID_CONFIG] = "quantiq-core-group"
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java

        // Consumer Settings
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // 처음부터 읽기
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = true
        props[ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG] = 1000 // 1초마다 커밋

        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory()
        return factory
    }
}
