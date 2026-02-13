package com.quantjumpstock.core.adapter.output.persistence.mongodb

import com.quantjumpstock.core.domain.model.prediction.VertexAIPredictionResult
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
    private val predictionResultMongoRepository: VertexAIPredictionResultMongoRepository
) : PredictionRepositoryPort {

    override fun findByDate(date: LocalDate): List<VertexAIPredictionResult> {
        return predictionResultMongoRepository.findByDate(date)
    }

    override fun findBySymbolOrderByDateDesc(symbol: String): List<VertexAIPredictionResult> {
        return predictionResultMongoRepository.findBySymbolOrderByDateDesc(symbol)
    }

    override fun findRecentPredictions(fromDate: LocalDate): List<VertexAIPredictionResult> {
        return predictionResultMongoRepository.findRecentPredictions(fromDate)
    }

    override fun findHighConfidenceBuySignals(date: LocalDate, minConfidence: Double): List<VertexAIPredictionResult> {
        return predictionResultMongoRepository.findHighConfidenceBuySignals(date, minConfidence)
    }
}
