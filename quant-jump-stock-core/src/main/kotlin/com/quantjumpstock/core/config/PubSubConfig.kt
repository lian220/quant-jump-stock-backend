package com.quantjumpstock.core.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

/**
 * Google Cloud Pub/Sub 설정
 *
 * LightweightPubSubPublisher가 google-cloud-pubsub API를 직접 사용합니다.
 * PUBSUB_EMULATOR_HOST 환경변수가 설정되면 자동으로 에뮬레이터를 사용합니다.
 * messaging.provider=pubsub 일 때 활성화됩니다.
 */
@Configuration
@ConditionalOnProperty(name = ["messaging.provider"], havingValue = "pubsub", matchIfMissing = true)
class PubSubConfig
