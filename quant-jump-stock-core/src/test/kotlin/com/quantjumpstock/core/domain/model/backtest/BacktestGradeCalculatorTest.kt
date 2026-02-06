package com.quantjumpstock.core.domain.model.backtest

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * BacktestGradeCalculator 단위 테스트
 * SCRUM-245: 성과 등급 계산 로직
 */
@DisplayName("BacktestGradeCalculator 단위 테스트")
class BacktestGradeCalculatorTest {

    @Nested
    @DisplayName("CAGR 등급")
    inner class CagrGrade {

        @Test
        @DisplayName("CAGR > 10% → GOOD")
        fun `high cagr returns good`() {
            val result = BacktestGradeCalculator.gradeCagr(BigDecimal("15.5"))
            assertEquals(MetricGrade.GOOD, result.grade)
            assertEquals("cagr", result.name)
            assertEquals("연평균 수익률", result.nameKo)
        }

        @Test
        @DisplayName("CAGR 0~10% → WARNING")
        fun `moderate cagr returns warning`() {
            val result = BacktestGradeCalculator.gradeCagr(BigDecimal("5.0"))
            assertEquals(MetricGrade.WARNING, result.grade)
        }

        @Test
        @DisplayName("CAGR 0% → WARNING (경계값)")
        fun `zero cagr returns warning`() {
            val result = BacktestGradeCalculator.gradeCagr(BigDecimal.ZERO)
            assertEquals(MetricGrade.WARNING, result.grade)
        }

        @Test
        @DisplayName("CAGR < 0% → DANGER")
        fun `negative cagr returns danger`() {
            val result = BacktestGradeCalculator.gradeCagr(BigDecimal("-3.2"))
            assertEquals(MetricGrade.DANGER, result.grade)
        }
    }

    @Nested
    @DisplayName("MDD 등급")
    inner class MddGrade {

        @Test
        @DisplayName("MDD > -15% → GOOD")
        fun `small drawdown returns good`() {
            val result = BacktestGradeCalculator.gradeMdd(BigDecimal("-10.0"))
            assertEquals(MetricGrade.GOOD, result.grade)
        }

        @Test
        @DisplayName("MDD -15%~-30% → WARNING")
        fun `moderate drawdown returns warning`() {
            val result = BacktestGradeCalculator.gradeMdd(BigDecimal("-22.5"))
            assertEquals(MetricGrade.WARNING, result.grade)
        }

        @Test
        @DisplayName("MDD < -30% → DANGER")
        fun `large drawdown returns danger`() {
            val result = BacktestGradeCalculator.gradeMdd(BigDecimal("-45.0"))
            assertEquals(MetricGrade.DANGER, result.grade)
        }

        @Test
        @DisplayName("양수 MDD도 처리")
        fun `positive mdd value handled`() {
            val result = BacktestGradeCalculator.gradeMdd(BigDecimal("5.0"))
            assertEquals(MetricGrade.GOOD, result.grade)
        }
    }

    @Nested
    @DisplayName("Sharpe Ratio 등급")
    inner class SharpeGrade {

        @Test
        @DisplayName("Sharpe > 1.0 → GOOD")
        fun `high sharpe returns good`() {
            val result = BacktestGradeCalculator.gradeSharpe(BigDecimal("1.5"))
            assertEquals(MetricGrade.GOOD, result.grade)
        }

        @Test
        @DisplayName("Sharpe 0.5~1.0 → WARNING")
        fun `moderate sharpe returns warning`() {
            val result = BacktestGradeCalculator.gradeSharpe(BigDecimal("0.7"))
            assertEquals(MetricGrade.WARNING, result.grade)
        }

        @Test
        @DisplayName("Sharpe < 0.5 → DANGER")
        fun `low sharpe returns danger`() {
            val result = BacktestGradeCalculator.gradeSharpe(BigDecimal("0.2"))
            assertEquals(MetricGrade.DANGER, result.grade)
        }

        @Test
        @DisplayName("null → WARNING (데이터 부족)")
        fun `null sharpe returns warning`() {
            val result = BacktestGradeCalculator.gradeSharpe(null)
            assertEquals(MetricGrade.WARNING, result.grade)
            assertNull(result.value)
            assertEquals("-", result.formattedValue)
        }
    }

    @Nested
    @DisplayName("승률 등급")
    inner class WinRateGrade {

        @Test
        @DisplayName("승률 > 55% → GOOD")
        fun `high win rate returns good`() {
            val result = BacktestGradeCalculator.gradeWinRate(BigDecimal("62.0"))
            assertEquals(MetricGrade.GOOD, result.grade)
        }

        @Test
        @DisplayName("승률 40~55% → WARNING")
        fun `moderate win rate returns warning`() {
            val result = BacktestGradeCalculator.gradeWinRate(BigDecimal("48.0"))
            assertEquals(MetricGrade.WARNING, result.grade)
        }

        @Test
        @DisplayName("승률 < 40% → DANGER")
        fun `low win rate returns danger`() {
            val result = BacktestGradeCalculator.gradeWinRate(BigDecimal("30.0"))
            assertEquals(MetricGrade.DANGER, result.grade)
        }

        @Test
        @DisplayName("null → WARNING")
        fun `null win rate returns warning`() {
            val result = BacktestGradeCalculator.gradeWinRate(null)
            assertEquals(MetricGrade.WARNING, result.grade)
        }
    }

    @Nested
    @DisplayName("Sortino Ratio 등급")
    inner class SortinoGrade {

        @Test
        @DisplayName("Sortino > 1.5 → GOOD")
        fun `high sortino returns good`() {
            val result = BacktestGradeCalculator.gradeSortino(BigDecimal("2.0"))
            assertEquals(MetricGrade.GOOD, result.grade)
        }

        @Test
        @DisplayName("Sortino 0.5~1.5 → WARNING")
        fun `moderate sortino returns warning`() {
            val result = BacktestGradeCalculator.gradeSortino(BigDecimal("1.0"))
            assertEquals(MetricGrade.WARNING, result.grade)
        }

        @Test
        @DisplayName("Sortino < 0.5 → DANGER")
        fun `low sortino returns danger`() {
            val result = BacktestGradeCalculator.gradeSortino(BigDecimal("0.3"))
            assertEquals(MetricGrade.DANGER, result.grade)
        }
    }

    @Nested
    @DisplayName("변동성 등급")
    inner class VolatilityGrade {

        @Test
        @DisplayName("변동성 < 15% → GOOD")
        fun `low volatility returns good`() {
            val result = BacktestGradeCalculator.gradeVolatility(BigDecimal("10.0"))
            assertEquals(MetricGrade.GOOD, result.grade)
        }

        @Test
        @DisplayName("변동성 15~25% → WARNING")
        fun `moderate volatility returns warning`() {
            val result = BacktestGradeCalculator.gradeVolatility(BigDecimal("20.0"))
            assertEquals(MetricGrade.WARNING, result.grade)
        }

        @Test
        @DisplayName("변동성 > 25% → DANGER")
        fun `high volatility returns danger`() {
            val result = BacktestGradeCalculator.gradeVolatility(BigDecimal("35.0"))
            assertEquals(MetricGrade.DANGER, result.grade)
        }
    }

    @Nested
    @DisplayName("종합 등급")
    inner class OverallGrade {

        @Test
        @DisplayName("대부분 GOOD → 종합 GOOD")
        fun `mostly good metrics returns good overall`() {
            val grade = BacktestGradeCalculator.calculateOverallGrade(
                cagr = BigDecimal("15.0"),
                mdd = BigDecimal("-10.0"),
                sharpe = BigDecimal("1.5"),
                winRate = BigDecimal("60.0")
            )
            assertEquals(MetricGrade.GOOD, grade)
        }

        @Test
        @DisplayName("DANGER 2개 이상 → 종합 DANGER")
        fun `two or more danger returns danger overall`() {
            val grade = BacktestGradeCalculator.calculateOverallGrade(
                cagr = BigDecimal("-5.0"),
                mdd = BigDecimal("-50.0"),
                sharpe = BigDecimal("0.3"),
                winRate = BigDecimal("60.0")
            )
            assertEquals(MetricGrade.DANGER, grade)
        }

        @Test
        @DisplayName("혼합 지표 → 종합 WARNING")
        fun `mixed metrics returns warning overall`() {
            val grade = BacktestGradeCalculator.calculateOverallGrade(
                cagr = BigDecimal("12.0"),
                mdd = BigDecimal("-25.0"),
                sharpe = BigDecimal("0.7"),
                winRate = BigDecimal("45.0")
            )
            assertEquals(MetricGrade.WARNING, grade)
        }

        @Test
        @DisplayName("null 지표가 있어도 계산 가능")
        fun `handles null metrics`() {
            // sharpe=null, winRate=null → GOOD 2개 / 총 2개 → 3개 미만이므로 WARNING
            val grade = BacktestGradeCalculator.calculateOverallGrade(
                cagr = BigDecimal("15.0"),
                mdd = BigDecimal("-10.0"),
                sharpe = null,
                winRate = null
            )
            assertEquals(MetricGrade.WARNING, grade)
        }
    }

    @Nested
    @DisplayName("평문 요약 생성")
    inner class PlainSummary {

        @Test
        @DisplayName("수익 시나리오 요약 생성")
        fun `generates summary for profitable result`() {
            val result = createTestResult(
                initialCapital = BigDecimal("10000000"),
                finalValue = BigDecimal("15230000"),
                cagr = BigDecimal("8.8"),
                mdd = BigDecimal("-15.2"),
                totalTrades = 45,
                winRate = BigDecimal("62.0")
            )

            val summary = BacktestGradeCalculator.generatePlainSummary(result)

            assertTrue(summary.contains("1,000만원"), "초기 자본 포함: $summary")
            assertTrue(summary.contains("1,523만원"), "최종 금액 포함: $summary")
            assertTrue(summary.contains("수익"), "수익 표시: $summary")
            assertTrue(summary.contains("8.8%"), "CAGR 포함: $summary")
            assertTrue(summary.contains("15.2%"), "MDD 포함: $summary")
            assertTrue(summary.contains("45"), "총 거래 수 포함: $summary")
            assertTrue(summary.contains("62.0%"), "승률 포함: $summary")
        }

        @Test
        @DisplayName("손실 시나리오 요약 생성")
        fun `generates summary for losing result`() {
            val result = createTestResult(
                initialCapital = BigDecimal("10000000"),
                finalValue = BigDecimal("8500000"),
                cagr = BigDecimal("-3.2"),
                mdd = BigDecimal("-25.0"),
                totalTrades = 20,
                winRate = BigDecimal("35.0")
            )

            val summary = BacktestGradeCalculator.generatePlainSummary(result)

            assertTrue(summary.contains("손실"), "손실 표시: $summary")
            assertTrue(summary.contains("-3.2%"), "음수 CAGR 포함: $summary")
        }

        @Test
        @DisplayName("1억 이상 금액 표시")
        fun `formats large amounts with eok`() {
            val result = createTestResult(
                initialCapital = BigDecimal("100000000"),
                finalValue = BigDecimal("152300000"),
                cagr = BigDecimal("12.0"),
                mdd = BigDecimal("-10.0")
            )

            val summary = BacktestGradeCalculator.generatePlainSummary(result)

            assertTrue(summary.contains("1억"), "1억 표시: $summary")
        }
    }

    private fun createTestResult(
        initialCapital: BigDecimal = BigDecimal("10000000"),
        finalValue: BigDecimal = BigDecimal("15000000"),
        cagr: BigDecimal = BigDecimal("10.0"),
        mdd: BigDecimal = BigDecimal("-15.0"),
        totalTrades: Int = 30,
        winRate: BigDecimal? = BigDecimal("55.0")
    ): BacktestResult {
        return BacktestResult(
            id = 1L,
            strategyId = 1L,
            strategyName = "테스트 전략",
            startDate = LocalDate.of(2021, 1, 1),
            endDate = LocalDate.of(2025, 12, 31),
            initialCapital = initialCapital,
            finalValue = finalValue,
            totalReturn = finalValue.subtract(initialCapital).divide(initialCapital, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal("100")),
            cagr = cagr,
            mdd = mdd,
            sharpeRatio = BigDecimal("1.2"),
            sortinoRatio = BigDecimal("1.8"),
            volatility = BigDecimal("18.0"),
            winRate = winRate,
            totalTrades = totalTrades,
            winningTrades = (totalTrades * 0.6).toInt(),
            losingTrades = totalTrades - (totalTrades * 0.6).toInt(),
            status = BacktestStatus.COMPLETED,
            createdAt = LocalDateTime.now(),
            completedAt = LocalDateTime.now()
        )
    }
}
