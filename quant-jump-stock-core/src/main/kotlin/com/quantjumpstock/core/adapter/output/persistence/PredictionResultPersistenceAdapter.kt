package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.mongodb.VertexAIPredictionResultMongoRepository
import com.quantjumpstock.core.domain.model.prediction.VertexAIPredictionResult
import com.quantjumpstock.core.domain.port.output.VertexAIPredictionResultRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * VertexAIPredictionResult Persistence Adapter (Output Adapter)
 * VertexAIPredictionResultRepository 인터페이스를 구현하여 MongoDB와 연동합니다.
 */
@Component
class VertexAIPredictionResultPersistenceAdapter(
    private val predictionResultMongoRepository: VertexAIPredictionResultMongoRepository
) : VertexAIPredictionResultRepository {

    override fun save(result: VertexAIPredictionResult): VertexAIPredictionResult {
        return predictionResultMongoRepository.save(result)
    }

    override fun findById(id: String): VertexAIPredictionResult? {
        return predictionResultMongoRepository.findById(id).orElse(null)
    }

    override fun findBySymbol(symbol: String): List<VertexAIPredictionResult> {
        return predictionResultMongoRepository.findBySymbolOrderByDateDesc(symbol)
    }

    override fun findByDate(date: LocalDate): List<VertexAIPredictionResult> {
        return predictionResultMongoRepository.findByDate(date)
    }

    override fun findBySymbolAndDate(symbol: String, date: LocalDate): VertexAIPredictionResult? {
        return predictionResultMongoRepository.findBySymbolOrderByDateDesc(symbol)
            .firstOrNull { it.date == date }
    }

    override fun findHighConfidenceBuySignals(date: LocalDate, minConfidence: Double): List<VertexAIPredictionResult> {
        return predictionResultMongoRepository.findHighConfidenceBuySignals(date, minConfidence)
    }

    override fun findLatestBySymbol(symbol: String): VertexAIPredictionResult? {
        return predictionResultMongoRepository.findBySymbolOrderByDateDesc(symbol).firstOrNull()
    }

    override fun deleteById(id: String) {
        predictionResultMongoRepository.deleteById(id)
    }
}
