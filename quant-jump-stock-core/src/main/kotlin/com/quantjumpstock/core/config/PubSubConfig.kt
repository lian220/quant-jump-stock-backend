package com.quantjumpstock.core.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

/**
 * Google Cloud Pub/Sub 설정
 *
 * Spring Cloud GCP auto-configuration이 PubSubTemplate을 자동 생성합니다.
 * PUBSUB_EMULATOR_HOST 환경변수가 설정되면 자동으로 에뮬레이터를 사용합니다.
 * messaging.provider=pubsub 일 때 활성화됩니다.
 * Kafka는 완전 제거되었으며, 모든 환경에서 Pub/Sub을 사용합니다.
 */
@Configuration
@ConditionalOnProperty(name = ["messaging.provider"], havingValue = "pubsub", matchIfMissing = true)
class PubSubConfig
