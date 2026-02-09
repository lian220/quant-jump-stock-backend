package com.quantjumpstock.core.domain

import java.time.LocalDateTime
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

/**
 * 주식 예측 결과
 * MongoDB stock_predictions 컬렉션과 매핑
 */
@Document(collection = "stock_predictions")
@CompoundIndexes(
    CompoundIndex(
        name = "date_ticker_unique",
        def = "{'date': 1, 'ticker': 1}",
        unique = true,
        background = true
    )
)
data class StockPrediction(
        @Id val id: String? = null,
        val ticker: String,
        val date: LocalDateTime,
        @Field("predicted_price") val predictedPrice: Double? = null,
        @Field("actual_price") val actualPrice: Double? = null,
        @Field("error") val error: Double? = null,
        @Field("predicted_rise") val predictedRise: Boolean? = null,
        @Field("actual_rise") val actualRise: Boolean? = null,
        @Field("created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
        @Field("updated_at") val updatedAt: LocalDateTime = LocalDateTime.now()
)
