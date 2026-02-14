package com.quantjumpstock.core.config

import org.springframework.context.annotation.Configuration

/**
 * Google Cloud Pub/Sub 설정
 *
 * Spring Cloud GCP auto-configuration이 PubSubTemplate을 자동 생성합니다.
 * PUBSUB_EMULATOR_HOST 환경변수가 설정되면 자동으로 에뮬레이터를 사용합니다.
 */
@Configuration
class PubSubConfig
