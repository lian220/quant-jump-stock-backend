package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import java.time.LocalDate

/**
 * PredictionResult Repository Port - 도메인 포트
 */
interface PredictionResultRepository {

    fun save(result: PredictionResult): PredictionResult

    fun findById(id: String): PredictionResult?

    fun findBySymbol(symbol: String): List<PredictionResult>

    fun findByDate(date: LocalDate): List<PredictionResult>

    fun findBySymbolAndDate(symbol: String, date: LocalDate): PredictionResult?

    /**
     * 높은 신뢰도의 매수 신호 조회
     */
    fun findHighConfidenceBuySignals(date: LocalDate, minConfidence: Double): List<PredictionResult>

    /**
     * 최신 예측 결과 조회
     */
    fun findLatestBySymbol(symbol: String): PredictionResult?

    fun deleteById(id: String)
}
