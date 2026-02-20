package com.quantjumpstock.core.domain

import java.time.LocalDateTime
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

@Document(collection = "sentiment_analysis")
@CompoundIndexes(
    CompoundIndex(
        name = "ticker_date_unique",
        def = "{'ticker': 1, 'date': 1}",
        unique = true,
        background = true
    )
)
data class SentimentAnalysis(
        @Id val id: String? = null,
        val ticker: String,
        val date: String, // YYYY-MM-DD
        @Field("average_sentiment_score") val averageSentimentScore: Double,
        @Field("article_count") val articleCount: Int,
        @Field("updated_at") val updatedAt: LocalDateTime = LocalDateTime.now()
)
