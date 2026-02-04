package com.quantjumpstock.core.adapter.output.persistence.mongodb

import com.quantjumpstock.core.domain.StockRecommendation
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface StockRecommendationMongoRepository : MongoRepository<StockRecommendation, String> {
    fun findByDateAndIsRecommendedTrue(date: String): List<StockRecommendation>
    fun findByTickerAndDate(ticker: String, date: String): StockRecommendation?
}
