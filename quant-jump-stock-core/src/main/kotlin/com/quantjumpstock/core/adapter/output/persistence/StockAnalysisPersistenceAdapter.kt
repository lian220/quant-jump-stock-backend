package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.mongodb.StockAnalysisMongoRepository
import com.quantjumpstock.core.domain.StockAnalysis
import com.quantjumpstock.core.domain.port.output.StockAnalysisRepository
import org.springframework.stereotype.Component

/**
 * StockAnalysis Persistence Adapter (Output Adapter)
 * StockAnalysisRepository 인터페이스를 구현하여 MongoDB와 연동합니다.
 */
@Component
class StockAnalysisPersistenceAdapter(
    private val mongoRepository: StockAnalysisMongoRepository
) : StockAnalysisRepository {

    override fun findAll(): List<StockAnalysis> {
        return mongoRepository.findAll()
    }

    override fun findByTicker(ticker: String): List<StockAnalysis> {
        return mongoRepository.findByTicker(ticker)
    }

    override fun save(analysis: StockAnalysis): StockAnalysis {
        return mongoRepository.save(analysis)
    }
}
