package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.prediction.VertexAIPredictionResult
import java.time.LocalDate

/**
 * VertexAIPredictionResult Repository Port - 도메인 포트
 */
interface VertexAIPredictionResultRepository {

    fun save(result: VertexAIPredictionResult): VertexAIPredictionResult

    fun findById(id: String): VertexAIPredictionResult?

    fun findBySymbol(symbol: String): List<VertexAIPredictionResult>

    fun findByDate(date: LocalDate): List<VertexAIPredictionResult>

    fun findBySymbolAndDate(symbol: String, date: LocalDate): VertexAIPredictionResult?

    /**
     * 높은 신뢰도의 매수 신호 조회
     */
    fun findHighConfidenceBuySignals(date: LocalDate, minConfidence: Double): List<VertexAIPredictionResult>

    /**
     * 최신 예측 결과 조회
     */
    fun findLatestBySymbol(symbol: String): VertexAIPredictionResult?

    fun deleteById(id: String)
}
