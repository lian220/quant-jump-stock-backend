package com.quantjumpstock.core.domain.prediction.port.output

import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import java.time.LocalDate

/**
 * Prediction Repository Port (Output Port)
 *
 * 예측 결과 조회를 위한 추상 인터페이스.
 * Application Service에서 이 Port를 통해 영속성 계층에 접근.
 */
interface PredictionRepositoryPort {

    /**
     * 특정 날짜의 모든 예측 결과 조회
     */
    fun findByDate(date: LocalDate): List<PredictionResult>

    /**
     * 특정 심볼의 예측 결과 조회 (최신순)
     */
    fun findBySymbolOrderByDateDesc(symbol: String): List<PredictionResult>

    /**
     * 특정 날짜 이후의 예측 결과 조회
     */
    fun findRecentPredictions(fromDate: LocalDate): List<PredictionResult>

    /**
     * 특정 날짜, 신뢰도 이상의 매수 신호 조회
     */
    fun findHighConfidenceBuySignals(date: LocalDate, minConfidence: Double): List<PredictionResult>
}
