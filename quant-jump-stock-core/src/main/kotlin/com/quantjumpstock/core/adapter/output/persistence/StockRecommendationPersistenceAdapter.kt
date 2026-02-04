package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.mongodb.StockRecommendationMongoRepository
import com.quantjumpstock.core.domain.StockRecommendation
import com.quantjumpstock.core.domain.port.output.StockRecommendationRepository
import org.springframework.stereotype.Component

/**
 * StockRecommendation Persistence Adapter (Output Adapter)
 * StockRecommendationRepository 인터페이스를 구현하여 MongoDB와 연동합니다.
 */
@Component
class StockRecommendationPersistenceAdapter(
    private val mongoRepository: StockRecommendationMongoRepository
) : StockRecommendationRepository {

    override fun findAll(): List<StockRecommendation> {
        return mongoRepository.findAll()
    }

    override fun findByDateAndIsRecommendedTrue(date: String): List<StockRecommendation> {
        return mongoRepository.findByDateAndIsRecommendedTrue(date)
    }

    override fun findByTickerAndDate(ticker: String, date: String): StockRecommendation? {
        return mongoRepository.findByTickerAndDate(ticker, date)
    }

    override fun save(recommendation: StockRecommendation): StockRecommendation {
        return mongoRepository.save(recommendation)
    }
}
