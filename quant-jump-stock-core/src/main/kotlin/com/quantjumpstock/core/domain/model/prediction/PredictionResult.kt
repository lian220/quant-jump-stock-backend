package com.quantjumpstock.core.domain.model.prediction

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Vertex AI 예측 결과 (MongoDB)
 */
@Document(collection = "prediction_results")
data class PredictionResult(
    @Id
    val id: String? = null,

    @Field("symbol")
    val symbol: String,

    @Field("date")
    val date: LocalDate,

    @Field("predicted_price")
    val predictedPrice: Double,

    @Field("confidence")
    val confidence: Double,

    @Field("signal")
    val signal: String,  // BUY, SELL, HOLD

    @Field("predicted_change_percent")
    val predictedChangePercent: Double,

    @Field("technical_score")
    val technicalScore: Double? = null,

    @Field("sentiment_score")
    val sentimentScore: Double? = null,

    @Field("model_version")
    val modelVersion: String,

    @Field("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Field("vertex_ai_job_id")
    val vertexAIJobId: String? = null,

    @Field("metadata")
    val metadata: Map<String, Any>? = null
)
