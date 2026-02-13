package com.quantjumpstock.core.adapter.output.persistence.jpa.prediction

import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import com.quantjumpstock.core.domain.prediction.port.output.PredictionRepositoryPort
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

/**
 * PredictionRepositoryPort 구현체
 *
 * PostgreSQL prediction_results 테이블에서 통합 예측 결과 조회.
 * MongoDB가 아닌 PostgreSQL 기반으로 동작.
 */
@Component
@Primary  // ✅ PostgreSQL 기반 Adapter를 우선 사용
class PredictionResultRepositoryAdapter(
    private val jpaRepository: PredictionResultJpaRepository
) : PredictionRepositoryPort {

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
    override fun findBySymbolOrderByDateDesc(symbol: String): List<PredictionResult> {
        return jpaRepository.findByTickerOrderByAnalysisDateDesc(symbol)
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
     * ⚠️ minConfidence는 이제 Composite Score 기준 (0~7.5)
     *    예: minConfidence=5.25 → 5.25점 이상
     */
    override fun findHighConfidenceBuySignals(
        date: LocalDate,
        minConfidence: Double
    ): List<PredictionResult> {
        val minScore = BigDecimal.valueOf(minConfidence)

        return jpaRepository.findByAnalysisDateAndCompositeScoreGreaterThanEqualOrderByCompositeScoreDesc(
            date,
            minScore
        ).map { it.toDomain() }
    }
}
