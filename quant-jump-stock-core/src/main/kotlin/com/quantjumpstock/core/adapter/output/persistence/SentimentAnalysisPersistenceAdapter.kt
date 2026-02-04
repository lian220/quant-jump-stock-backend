package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.mongodb.SentimentAnalysisMongoRepository
import com.quantjumpstock.core.domain.SentimentAnalysis
import com.quantjumpstock.core.domain.port.output.SentimentAnalysisRepository
import org.springframework.stereotype.Component

/**
 * SentimentAnalysis Persistence Adapter (Output Adapter)
 * SentimentAnalysisRepository 인터페이스를 구현하여 MongoDB와 연동합니다.
 */
@Component
class SentimentAnalysisPersistenceAdapter(
    private val mongoRepository: SentimentAnalysisMongoRepository
) : SentimentAnalysisRepository {

    override fun findAll(): List<SentimentAnalysis> {
        return mongoRepository.findAll()
    }

    override fun findByTicker(ticker: String): List<SentimentAnalysis> {
        return mongoRepository.findByTicker(ticker)
    }

    override fun findByTickerAndDate(ticker: String, date: String): SentimentAnalysis? {
        return mongoRepository.findByTickerAndDate(ticker, date)
    }

    override fun save(analysis: SentimentAnalysis): SentimentAnalysis {
        return mongoRepository.save(analysis)
    }
}
