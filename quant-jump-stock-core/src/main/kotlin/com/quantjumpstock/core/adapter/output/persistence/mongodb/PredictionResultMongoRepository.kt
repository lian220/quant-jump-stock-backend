package com.quantjumpstock.core.adapter.output.persistence.mongodb

import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * Vertex AI 예측 결과 MongoDB Repository
 */
@Repository
interface PredictionResultMongoRepository : MongoRepository<PredictionResult, String> {

    /**
     * 특정 날짜의 모든 예측 결과 조회
     */
    fun findByDate(date: LocalDate): List<PredictionResult>

    /**
     * 특정 날짜의 매수 신호만 조회 (신뢰도 높은 순)
     */
    @Query("{ 'date': ?0, 'signal': 'BUY' }")
    fun findBuySignalsByDate(date: LocalDate): List<PredictionResult>

    /**
     * 특정 심볼의 최근 예측 결과 조회
     */
    fun findBySymbolOrderByDateDesc(symbol: String): List<PredictionResult>

    /**
     * 최근 예측 결과 조회 (날짜 내림차순)
     */
    @Query("{ 'date': { \$gte: ?0 } }")
    fun findRecentPredictions(fromDate: LocalDate): List<PredictionResult>

    /**
     * 특정 신뢰도 이상의 매수 신호 조회
     */
    @Query("{ 'date': ?0, 'signal': 'BUY', 'confidence': { \$gte: ?1 } }")
    fun findHighConfidenceBuySignals(date: LocalDate, minConfidence: Double): List<PredictionResult>

    /**
     * Vertex AI Job ID로 조회
     */
    fun findByVertexAIJobId(jobId: String): List<PredictionResult>
}
