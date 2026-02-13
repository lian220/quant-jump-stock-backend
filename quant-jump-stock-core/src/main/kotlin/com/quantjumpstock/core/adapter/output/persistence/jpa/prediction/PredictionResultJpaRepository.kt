package com.quantjumpstock.core.adapter.output.persistence.jpa.prediction

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 통합 예측 결과 JPA Repository
 */
@Repository
interface PredictionResultJpaRepository : JpaRepository<PredictionResultEntity, Long> {

    /**
     * 특정 날짜의 전체 예측 결과 조회 (Composite Score 내림차순)
     */
    fun findByAnalysisDateOrderByCompositeScoreDesc(date: LocalDate): List<PredictionResultEntity>

    /**
     * 특정 날짜의 고득점 매수 신호 조회
     *
     * @param date 분석 날짜
     * @param minScore 최소 Composite Score
     */
    fun findByAnalysisDateAndCompositeScoreGreaterThanEqualOrderByCompositeScoreDesc(
        date: LocalDate,
        minScore: BigDecimal
    ): List<PredictionResultEntity>

    /**
     * 특정 날짜의 추천 종목 조회 (추천 플래그 + 고득점순)
     */
    fun findByAnalysisDateAndIsRecommendedTrueOrderByCompositeScoreDesc(
        date: LocalDate
    ): List<PredictionResultEntity>

    /**
     * 특정 종목의 최근 예측 결과 조회 (날짜 내림차순)
     *
     * @param ticker 종목 티커
     */
    fun findByTickerOrderByAnalysisDateDesc(ticker: String): List<PredictionResultEntity>

    /**
     * 날짜 범위의 예측 결과 조회
     */
    fun findByAnalysisDateBetweenOrderByAnalysisDateDescCompositeScoreDesc(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<PredictionResultEntity>
}
