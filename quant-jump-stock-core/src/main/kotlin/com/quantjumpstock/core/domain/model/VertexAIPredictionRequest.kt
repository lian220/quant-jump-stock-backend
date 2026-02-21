package com.quantjumpstock.core.domain.model

/**
 * Vertex AI 예측 요청 Domain Model
 */
data class VertexAIPredictionRequest(
    val timestamp: String,
    val source: String,
    val requestId: String,
    val threadTs: String? = null,
    val envVars: Map<String, String> = HashMap()
)
