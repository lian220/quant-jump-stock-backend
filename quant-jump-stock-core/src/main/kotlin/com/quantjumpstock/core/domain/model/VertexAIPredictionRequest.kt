package com.quantjumpstock.core.domain.model

/**
 * Vertex AI 예측 요청 Domain Model
 *
 * Admin 긴급 수동 실행 시 Pub/Sub으로 전달되는 요청.
 * 정상 운영에서는 Cloud Scheduler가 Vertex AI API를 직접 호출합니다.
 */
data class VertexAIPredictionRequest(
    val timestamp: String,
    val source: String,
    val requestId: String,
    val threadTs: String? = null,
    val envVars: Map<String, String> = emptyMap()
)
