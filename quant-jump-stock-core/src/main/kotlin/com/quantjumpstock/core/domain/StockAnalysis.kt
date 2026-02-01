package com.quantjumpstock.core.domain

import java.time.LocalDateTime
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

@Document(collection = "stock_analysis_results")
data class StockAnalysis(
        @Id val id: String? = null,
        val ticker: String,
        val date: LocalDateTime,
        val metrics: AnalysisMetrics? = null,
        val predictions: AnalysisPredictions? = null,
        val recommendation: String? = null,
        val analysis: String? = null,
        @Field("created_at") val createdAt: LocalDateTime = LocalDateTime.now()
)

data class AnalysisMetrics(
        val mae: Double? = null,
        val rmse: Double? = null,
        val accuracy: Double? = null
)

data class AnalysisPredictions(
        @Field("last_actual_price") val lastActualPrice: Double? = null,
        @Field("predicted_future_price") val predictedFuturePrice: Double? = null,
        @Field("predicted_rise") val predictedRise: Boolean? = null,
        @Field("rise_probability") val riseProbability: Double? = null
)
