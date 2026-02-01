package com.quantjumpstock.core.adapter.output.persistence.mongodb

import com.quantjumpstock.core.domain.SentimentAnalysis
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface SentimentAnalysisRepository : MongoRepository<SentimentAnalysis, String> {
    fun findByTicker(ticker: String): List<SentimentAnalysis>
    fun findByTickerAndDate(ticker: String, date: String): SentimentAnalysis?
}
