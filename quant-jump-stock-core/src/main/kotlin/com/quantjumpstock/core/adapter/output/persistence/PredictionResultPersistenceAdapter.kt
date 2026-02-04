package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.mongodb.PredictionResultMongoRepository
import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import com.quantjumpstock.core.domain.port.output.PredictionResultRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * PredictionResult Persistence Adapter (Output Adapter)
 * PredictionResultRepository 인터페이스를 구현하여 MongoDB와 연동합니다.
 */
@Component
class PredictionResultPersistenceAdapter(
    private val predictionResultMongoRepository: PredictionResultMongoRepository
) : PredictionResultRepository {

    override fun save(result: PredictionResult): PredictionResult {
        return predictionResultMongoRepository.save(result)
    }

    override fun findById(id: String): PredictionResult? {
        return predictionResultMongoRepository.findById(id).orElse(null)
    }

    override fun findBySymbol(symbol: String): List<PredictionResult> {
        return predictionResultMongoRepository.findBySymbolOrderByDateDesc(symbol)
    }

    override fun findByDate(date: LocalDate): List<PredictionResult> {
        return predictionResultMongoRepository.findByDate(date)
    }

    override fun findBySymbolAndDate(symbol: String, date: LocalDate): PredictionResult? {
        return predictionResultMongoRepository.findBySymbolOrderByDateDesc(symbol)
            .firstOrNull { it.date == date }
    }

    override fun findHighConfidenceBuySignals(date: LocalDate, minConfidence: Double): List<PredictionResult> {
        return predictionResultMongoRepository.findHighConfidenceBuySignals(date, minConfidence)
    }

    override fun findLatestBySymbol(symbol: String): PredictionResult? {
        return predictionResultMongoRepository.findBySymbolOrderByDateDesc(symbol).firstOrNull()
    }

    override fun deleteById(id: String) {
        predictionResultMongoRepository.deleteById(id)
    }
}
