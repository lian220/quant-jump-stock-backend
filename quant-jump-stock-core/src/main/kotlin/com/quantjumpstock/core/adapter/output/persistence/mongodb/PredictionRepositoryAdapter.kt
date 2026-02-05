package com.quantjumpstock.core.adapter.output.persistence.mongodb

import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import com.quantjumpstock.core.domain.prediction.port.output.PredictionRepositoryPort
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Prediction Repository Adapter (Output Adapter)
 *
 * PredictionRepositoryPort를 구현하여 MongoDB에 접근.
 * Application Service는 이 Adapter가 아닌 Port를 통해 접근.
 */
@Component
class PredictionRepositoryAdapter(
    private val predictionResultMongoRepository: PredictionResultMongoRepository
) : PredictionRepositoryPort {

    override fun findByDate(date: LocalDate): List<PredictionResult> {
        return predictionResultMongoRepository.findByDate(date)
    }

    override fun findBySymbolOrderByDateDesc(symbol: String): List<PredictionResult> {
        return predictionResultMongoRepository.findBySymbolOrderByDateDesc(symbol)
    }

    override fun findRecentPredictions(fromDate: LocalDate): List<PredictionResult> {
        return predictionResultMongoRepository.findRecentPredictions(fromDate)
    }

    override fun findHighConfidenceBuySignals(date: LocalDate, minConfidence: Double): List<PredictionResult> {
        return predictionResultMongoRepository.findHighConfidenceBuySignals(date, minConfidence)
    }
}
