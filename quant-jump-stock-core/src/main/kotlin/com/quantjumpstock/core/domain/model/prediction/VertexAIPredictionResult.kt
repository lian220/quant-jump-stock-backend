package com.quantjumpstock.core.domain.model.prediction

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Vertex AI 예측 결과 (MongoDB)
 *
 * 기존 AI 예측 시스템용 도메인 모델.
 * Composite Score 기반 새 시스템은 PredictionResult 사용.
 */
@Document(collection = "prediction_results")
@CompoundIndexes(
    CompoundIndex(
        name = "idx_date_signal_confidence",
        def = "{'date': 1, 'signal': 1, 'confidence': -1}",
        background = true
    ),
    CompoundIndex(
        name = "idx_symbol_date",
        def = "{'symbol': 1, 'date': -1}",
        background = true
    )
)
data class VertexAIPredictionResult(
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

    @Indexed(background = true)
    @Field("vertex_ai_job_id")
    val vertexAIJobId: String? = null,

    @Field("metadata")
    val metadata: Map<String, Any>? = null
)
