package com.quantjumpstock.core.domain.prediction.port.output

import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import java.time.LocalDate

/**
 * Prediction Result Repository Port (Output Port)
 *
 * Composite Score 기반 통합 예측 결과 조회를 위한 추상 인터페이스 (PostgreSQL).
 * Application Service에서 이 Port를 통해 영속성 계층에 접근.
 *
 * Note: Vertex AI MongoDB 기반 시스템은 PredictionRepositoryPort 사용.
 */
interface PredictionResultRepositoryPort {

    /**
     * 특정 날짜의 모든 예측 결과 조회
     */
    fun findByDate(date: LocalDate): List<PredictionResult>

    /**
     * 특정 종목(ticker)의 예측 결과 조회 (최신순)
     */
    fun findByTickerOrderByDateDesc(ticker: String): List<PredictionResult>

    /**
     * 특정 날짜 이후의 예측 결과 조회
     */
    fun findRecentPredictions(fromDate: LocalDate): List<PredictionResult>

    /**
     * 특정 날짜, Composite Score 이상의 매수 신호 조회
     *
     * @param date 조회 날짜
     * @param minCompositeScore 최소 Composite Score (0~7.5)
     */
    fun findHighConfidenceBuySignals(date: LocalDate, minCompositeScore: Double): List<PredictionResult>
}
