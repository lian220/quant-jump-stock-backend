package com.quantjumpstock.core.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Vertex AI 설정
 *
 * Application Service에서 사용할 Vertex AI 관련 설정.
 * GCP Adapter의 GcpProperties와 분리하여 아키텍처 위반 방지.
 */
@Configuration
@ConfigurationProperties(prefix = "gcp.vertex-ai")
@ConditionalOnProperty(name = ["gcp.vertex-ai.enabled"], havingValue = "true", matchIfMissing = false)
class VertexAIConfig {
    var jobName: String = "quantiq-stock-prediction"
    var region: String = "asia-northeast3"
}
