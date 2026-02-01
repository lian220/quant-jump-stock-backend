package com.quantjumpstock.core.domain

import java.time.LocalDateTime
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

@Document(collection = "stock_recommendations")
data class StockRecommendation(
        @Id val id: String? = null,
        val ticker: String,
        val date: String, // YYYY-MM-DD
        @Field("stock_name") val stockName: String? = null,
        @Field("current_price") val currentPrice: Double? = null,
        @Field("composite_score") val compositeScore: Double? = null,
        @Field("technical_indicators") val technicalIndicators: TechnicalIndicators? = null,
        @Field("sentiment_score") val sentimentScore: Double? = null,
        @Field("recommendation_reason") val recommendationReason: String? = null,
        @Field("is_recommended") val isRecommended: Boolean = false,
        @Field("updated_at") val updatedAt: LocalDateTime = LocalDateTime.now()
)

data class TechnicalIndicators(
        val sma20: Double? = null,
        val sma50: Double? = null,
        val sma200: Double? = null,
        val rsi: Double? = null,
        val macd: Double? = null,
        val signal: Double? = null,
        @Field("macd_histogram") val macdHistogram: Double? = null,
        @Field("bollinger_upper") val bollingerUpper: Double? = null,
        @Field("bollinger_lower") val bollingerLower: Double? = null,
        val volume: Long? = null,
        @Field("avg_volume") val avgVolume: Long? = null
)
