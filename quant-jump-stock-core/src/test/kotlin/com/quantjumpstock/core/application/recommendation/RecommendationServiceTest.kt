package com.quantjumpstock.core.application.recommendation

import com.quantjumpstock.core.domain.model.prediction.CompositeGrade
import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import com.quantjumpstock.core.domain.port.output.StockPriceDataPort
import com.quantjumpstock.core.domain.prediction.port.output.PredictionResultRepositoryPort
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * RecommendationService — ADR 0006 Phase 2 검증.
 *
 * 핵심: Kotlin 은 점수를 재계산/재정규화하지 않는다(SSoT=scoring_spec.yaml).
 * 저장된 0~100 composite, grade, axis_contributions, is_recommended 를 그대로 통과시킨다.
 */
class RecommendationServiceTest {

    private val repository = mockk<PredictionResultRepositoryPort>()
    private val priceDataPort = mockk<StockPriceDataPort>(relaxed = true)
    private val sut = RecommendationService(repository, priceDataPort)

    private val date = LocalDate.of(2026, 6, 6)

    private fun aPrediction(
        ticker: String = "AAPL",
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
        ticker = ticker,
        stockName = "$ticker Inc",
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
        every { repository.findHighConfidenceBuySignals(date, any()) } returns
            listOf(aPrediction(compositeScore = BigDecimal("83.0")))

        val result = sut.getBuySignals(date, minConfidence = 0.6)

        val dto = result.buySignals.single()
        // 저장값 83.0 그대로 (재정규화 없음)
        assertThat(dto.compositeScore).isEqualByComparingTo(BigDecimal("83.0"))
    }

    @Test
    fun `should set composite display equal to composite score`() {
        every { repository.findHighConfidenceBuySignals(date, any()) } returns
            listOf(aPrediction(compositeScore = BigDecimal("70.0")))

        val dto = sut.getBuySignals(date).buySignals.single()

        // display == composite (동적 정규화 제거 확인)
        assertThat(dto.compositeScoreDisplay).isEqualTo(70.0)
    }

    @Test
    fun `should use stored grade without recomputing from thresholds`() {
        every { repository.findHighConfidenceBuySignals(date, any()) } returns
            listOf(aPrediction(compositeScore = BigDecimal("55.0"), compositeGrade = CompositeGrade.S))

        val dto = sut.getBuySignals(date).buySignals.single()

        // composite 55 이지만 저장 grade(S)를 그대로 사용 (Kotlin 재판정 금지)
        assertThat(dto.compositeGrade).isEqualTo("S")
    }

    @Test
    fun `should expose axis contributions and score coverage from stored values`() {
        every { repository.findHighConfidenceBuySignals(date, any()) } returns
            listOf(aPrediction(scoreCoverage = BigDecimal("0.800")))

        val dto = sut.getBuySignals(date).buySignals.single()

        assertThat(dto.axisContributions).containsEntry("tech", 42.5)
            .containsEntry("ai", 18.0)
            .containsEntry("sentiment", 9.5)
        assertThat(dto.scoreCoverage).isEqualTo(0.8)
        // per-axis display 도 axis_contributions pass-through
        assertThat(dto.techScoreDisplay).isEqualTo(42.5)
        assertThat(dto.aiScoreDisplay).isEqualTo(18.0)
        assertThat(dto.sentimentScoreDisplay).isEqualTo(9.5)
    }

    @Test
    fun `should filter out non-recommended results`() {
        every { repository.findHighConfidenceBuySignals(date, any()) } returns listOf(
            aPrediction(ticker = "AAPL", isRecommended = true),
            aPrediction(ticker = "TSLA", isRecommended = false)
        )

        val result = sut.getBuySignals(date)

        // is_recommended=true 만 통과 (coverage guard 결과 pass-through)
        assertThat(result.buySignals).hasSize(1)
        assertThat(result.buySignals.single().ticker).isEqualTo("AAPL")
        assertThat(result.buySignals.single().isRecommended).isTrue()
    }
}
