package com.quantjumpstock.core.application.prediction

import com.quantjumpstock.core.domain.model.prediction.CompositeGrade
import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import com.quantjumpstock.core.domain.port.output.StockPriceDataPort
import com.quantjumpstock.core.domain.prediction.port.output.PredictionResultRepositoryPort
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager
import java.math.BigDecimal
import java.time.LocalDate

/**
 * PredictionService — ADR 0006 Phase 2 검증 (Map DTO).
 *
 * 핵심: 동적 정규화/결측 축 재분배 제거. 저장된 0~100 값 + axis_contributions pass-through.
 */
class PredictionServiceTest {

    private val repository = mockk<PredictionResultRepositoryPort>()
    private val priceDataPort = mockk<StockPriceDataPort>(relaxed = true)
    // 캐시 비활성: getCache 가 null 반환 → 캐시 우회, 항상 DB 조회
    private val cacheManager = mockk<CacheManager> { every { getCache(any()) } returns null }
    private val sut = PredictionService(repository, priceDataPort, cacheManager)

    private val date = LocalDate.of(2026, 6, 6)

    private fun aPrediction(
        compositeScore: BigDecimal = BigDecimal("70.0"),
        compositeGrade: CompositeGrade = CompositeGrade.A,
        isRecommended: Boolean = true,
        axisContributions: Map<String, BigDecimal> = mapOf(
            "tech" to BigDecimal("42.50"),
            "ai" to BigDecimal("18.00"),
            "sentiment" to BigDecimal("9.50")
        ),
        scoreCoverage: BigDecimal = BigDecimal("1.000")
    ) = PredictionResult(
        ticker = "AAPL",
        stockName = "AAPL Inc",
        analysisDate = date,
        aiScore = BigDecimal("6.0"),
        techScore = BigDecimal("2.5"),
        sentimentNormalizedScore = BigDecimal("4.75"),
        compositeScore = compositeScore,
        compositeGrade = compositeGrade,
        isRecommended = isRecommended,
        axisContributions = axisContributions,
        scoreCoverage = scoreCoverage
    )

    @Test
    fun `should pass through composite score 0 to 100 without recalculation`() {
        every { repository.findLatestAnalysisDate() } returns date
        every { repository.findByDate(date) } returns listOf(aPrediction(compositeScore = BigDecimal("83.0")))

        val dto = sut.getLatestPredictions().predictions.single()

        assertThat(dto["compositeScore"]).isEqualTo(83.0)
        // display == composite (동적 정규화 제거)
        assertThat(dto["compositeScoreDisplay"]).isEqualTo(83.0)
    }

    @Test
    fun `should expose axis contributions as per-axis display and stored grade`() {
        every { repository.findByDate(date) } returns listOf(
            aPrediction(compositeScore = BigDecimal("55.0"), compositeGrade = CompositeGrade.S)
        )

        val dto = sut.getPredictionsByDate(date).predictions.single()

        // 저장 grade 그대로
        assertThat(dto["compositeGrade"]).isEqualTo("S")
        // axis_contributions pass-through (정적 max 기반 정규화 아님)
        assertThat(dto["techScoreDisplay"]).isEqualTo(42.5)
        assertThat(dto["aiScoreDisplay"]).isEqualTo(18.0)
        assertThat(dto["sentimentScoreDisplay"]).isEqualTo(9.5)

        @Suppress("UNCHECKED_CAST")
        val axis = dto["axisContributions"] as Map<String, Double>
        assertThat(axis).containsEntry("tech", 42.5)
        assertThat(dto["scoreCoverage"]).isEqualTo(1.0)
        assertThat(dto["isRecommended"]).isEqualTo(true)
    }

    @Test
    fun `should convert minConfidence to 0 to 100 filter scale`() {
        every { repository.findLatestAnalysisDate() } returns date
        // minConfidence 0.6 → minCompositeScore 60.0 으로 변환되어 repo 에 전달
        every { repository.findHighConfidenceBuySignals(date, 60.0) } returns listOf(aPrediction())

        val result = sut.getBuySignals(minConfidence = 0.6)

        assertThat(result.count).isEqualTo(1)
    }
}
