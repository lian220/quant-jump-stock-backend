package com.quantjumpstock.core.adapter.output.persistence.jpa.prediction

import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import com.quantjumpstock.core.domain.prediction.port.output.PredictionResultRepositoryPort
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

/**
 * PredictionResultRepositoryPort 구현체
 *
 * PostgreSQL prediction_results 테이블에서 Composite Score 기반 통합 예측 결과 조회.
 */
@Component
class PredictionResultRepositoryAdapter(
    private val jpaRepository: PredictionResultJpaRepository
) : PredictionResultRepositoryPort {

    /**
     * 특정 날짜의 모든 예측 결과 조회
     */
    override fun findByDate(date: LocalDate): List<PredictionResult> {
        return jpaRepository.findByAnalysisDateOrderByCompositeScoreDesc(date)
            .map { it.toDomain() }
    }

    /**
     * 특정 종목의 예측 결과 조회 (최신순)
     */
    override fun findByTickerOrderByDateDesc(ticker: String): List<PredictionResult> {
        return jpaRepository.findByTickerOrderByAnalysisDateDesc(ticker)
            .map { it.toDomain() }
    }

    /**
     * 특정 날짜 이후의 예측 결과 조회
     */
    override fun findRecentPredictions(fromDate: LocalDate): List<PredictionResult> {
        val toDate = LocalDate.now()
        return jpaRepository.findByAnalysisDateBetweenOrderByAnalysisDateDescCompositeScoreDesc(
            fromDate,
            toDate
        ).map { it.toDomain() }
    }

    /**
     * 특정 날짜, Composite Score 이상의 매수 신호 조회
     *
     * ⚠️ minCompositeScore: Composite Score 기준 (0~100, ADR 0006)
     *    예: minCompositeScore=60 → 60점 이상
     */
    override fun findHighConfidenceBuySignals(
        date: LocalDate,
        minCompositeScore: Double
    ): List<PredictionResult> {
        val minScore = BigDecimal.valueOf(minCompositeScore)

        return jpaRepository.findByAnalysisDateAndCompositeScoreGreaterThanEqualOrderByCompositeScoreDesc(
            date,
            minScore
        ).map { it.toDomain() }
    }

    /**
     * 가장 최근 분석 날짜 조회
     */
    override fun findLatestAnalysisDate(): LocalDate? {
        return jpaRepository.findLatestAnalysisDate()
    }
}
