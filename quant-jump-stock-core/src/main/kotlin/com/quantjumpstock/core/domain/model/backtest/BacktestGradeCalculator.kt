package com.quantjumpstock.core.domain.model.backtest

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * 백테스트 성과 등급 (신호등 시스템)
 */
enum class MetricGrade(val label: String, val labelKo: String) {
    GOOD("good", "좋음"),
    WARNING("warning", "보통"),
    DANGER("danger", "위험")
}

/**
 * 등급이 포함된 지표 정보
 */
data class GradedMetric(
    val name: String,
    val nameKo: String,
    val value: BigDecimal?,
    val formattedValue: String,
    val grade: MetricGrade,
    val description: String
)

/**
 * 백테스트 성과 등급 계산기
 *
 * PRD v2 기준 신호등 시스템:
 * - GOOD (🟢 좋음): 투자자에게 긍정적인 지표
 * - WARNING (🟡 보통): 주의가 필요한 수준
 * - DANGER (🔴 위험): 리스크가 높은 수준
 */
object BacktestGradeCalculator {

    /**
     * CAGR (연평균 복리 수익률) 등급
     * - GOOD: > 10%
     * - WARNING: 0% ~ 10%
     * - DANGER: < 0%
     */
    fun gradeCagr(cagr: BigDecimal): GradedMetric {
        val grade = when {
            cagr > BigDecimal("10") -> MetricGrade.GOOD
            cagr >= BigDecimal.ZERO -> MetricGrade.WARNING
            else -> MetricGrade.DANGER
        }
        val desc = when (grade) {
            MetricGrade.GOOD -> "시장 평균(약 8~10%)을 웃도는 우수한 수익률입니다."
            MetricGrade.WARNING -> "양(+)의 수익이지만, 시장 평균에 미치지 못할 수 있습니다."
            MetricGrade.DANGER -> "원금 손실이 발생하는 구간입니다."
        }
        return GradedMetric(
            name = "cagr",
            nameKo = "연평균 수익률",
            value = cagr,
            formattedValue = "${formatPercent(cagr)}%",
            grade = grade,
            description = desc
        )
    }

    /**
     * MDD (최대 낙폭) 등급
     * - GOOD: > -15% (낙폭 작음)
     * - WARNING: -15% ~ -30%
     * - DANGER: < -30% (낙폭 큼)
     */
    fun gradeMdd(mdd: BigDecimal): GradedMetric {
        val absMdd = mdd.abs()
        val grade = when {
            absMdd < BigDecimal("15") -> MetricGrade.GOOD
            absMdd <= BigDecimal("30") -> MetricGrade.WARNING
            else -> MetricGrade.DANGER
        }
        val desc = when (grade) {
            MetricGrade.GOOD -> "낙폭이 작아 안정적인 전략입니다."
            MetricGrade.WARNING -> "중간 수준의 낙폭입니다. 일시적 손실을 견딜 수 있어야 합니다."
            MetricGrade.DANGER -> "큰 폭의 하락이 있었습니다. 높은 위험 감수가 필요합니다."
        }
        return GradedMetric(
            name = "mdd",
            nameKo = "최대 낙폭",
            value = mdd,
            formattedValue = "${formatPercent(mdd)}%",
            grade = grade,
            description = desc
        )
    }

    /**
     * Sharpe Ratio 등급
     * - GOOD: > 1.0
     * - WARNING: 0.5 ~ 1.0
     * - DANGER: < 0.5
     */
    fun gradeSharpe(sharpe: BigDecimal?): GradedMetric {
        if (sharpe == null) {
            return GradedMetric(
                name = "sharpeRatio",
                nameKo = "샤프 비율",
                value = null,
                formattedValue = "-",
                grade = MetricGrade.WARNING,
                description = "데이터가 부족하여 계산할 수 없습니다."
            )
        }
        val grade = when {
            sharpe > BigDecimal("1.0") -> MetricGrade.GOOD
            sharpe >= BigDecimal("0.5") -> MetricGrade.WARNING
            else -> MetricGrade.DANGER
        }
        val desc = when (grade) {
            MetricGrade.GOOD -> "위험 대비 수익이 뛰어납니다."
            MetricGrade.WARNING -> "위험 대비 수익이 보통 수준입니다."
            MetricGrade.DANGER -> "감수하는 위험에 비해 수익이 부족합니다."
        }
        return GradedMetric(
            name = "sharpeRatio",
            nameKo = "샤프 비율",
            value = sharpe,
            formattedValue = formatDecimal(sharpe),
            grade = grade,
            description = desc
        )
    }

    /**
     * 승률 등급
     * - GOOD: > 55%
     * - WARNING: 40% ~ 55%
     * - DANGER: < 40%
     */
    fun gradeWinRate(winRate: BigDecimal?): GradedMetric {
        if (winRate == null) {
            return GradedMetric(
                name = "winRate",
                nameKo = "승률",
                value = null,
                formattedValue = "-",
                grade = MetricGrade.WARNING,
                description = "거래 데이터가 부족합니다."
            )
        }
        val grade = when {
            winRate > BigDecimal("55") -> MetricGrade.GOOD
            winRate >= BigDecimal("40") -> MetricGrade.WARNING
            else -> MetricGrade.DANGER
        }
        val desc = when (grade) {
            MetricGrade.GOOD -> "절반 이상의 거래에서 수익이 발생합니다."
            MetricGrade.WARNING -> "승률이 보통 수준입니다. 손익비를 함께 확인하세요."
            MetricGrade.DANGER -> "수익 거래보다 손실 거래가 많습니다."
        }
        return GradedMetric(
            name = "winRate",
            nameKo = "승률",
            value = winRate,
            formattedValue = "${formatPercent(winRate)}%",
            grade = grade,
            description = desc
        )
    }

    /**
     * Sortino Ratio 등급
     * - GOOD: > 1.5
     * - WARNING: 0.5 ~ 1.5
     * - DANGER: < 0.5
     */
    fun gradeSortino(sortino: BigDecimal?): GradedMetric {
        if (sortino == null) {
            return GradedMetric(
                name = "sortinoRatio",
                nameKo = "소르티노 비율",
                value = null,
                formattedValue = "-",
                grade = MetricGrade.WARNING,
                description = "데이터가 부족하여 계산할 수 없습니다."
            )
        }
        val grade = when {
            sortino > BigDecimal("1.5") -> MetricGrade.GOOD
            sortino >= BigDecimal("0.5") -> MetricGrade.WARNING
            else -> MetricGrade.DANGER
        }
        val desc = when (grade) {
            MetricGrade.GOOD -> "하락 위험 대비 수익이 우수합니다."
            MetricGrade.WARNING -> "하락 위험 대비 수익이 보통입니다."
            MetricGrade.DANGER -> "하락 위험이 수익에 비해 큽니다."
        }
        return GradedMetric(
            name = "sortinoRatio",
            nameKo = "소르티노 비율",
            value = sortino,
            formattedValue = formatDecimal(sortino),
            grade = grade,
            description = desc
        )
    }

    /**
     * 변동성 등급
     * - GOOD: < 15%
     * - WARNING: 15% ~ 25%
     * - DANGER: > 25%
     */
    fun gradeVolatility(volatility: BigDecimal?): GradedMetric {
        if (volatility == null) {
            return GradedMetric(
                name = "volatility",
                nameKo = "변동성",
                value = null,
                formattedValue = "-",
                grade = MetricGrade.WARNING,
                description = "데이터가 부족합니다."
            )
        }
        val grade = when {
            volatility < BigDecimal("15") -> MetricGrade.GOOD
            volatility <= BigDecimal("25") -> MetricGrade.WARNING
            else -> MetricGrade.DANGER
        }
        val desc = when (grade) {
            MetricGrade.GOOD -> "수익률이 안정적입니다. 예측 가능한 수준입니다."
            MetricGrade.WARNING -> "보통 수준의 변동성입니다."
            MetricGrade.DANGER -> "수익률 변동이 매우 큽니다. 큰 수익과 큰 손실이 반복될 수 있습니다."
        }
        return GradedMetric(
            name = "volatility",
            nameKo = "변동성",
            value = volatility,
            formattedValue = "${formatPercent(volatility)}%",
            grade = grade,
            description = desc
        )
    }

    /**
     * 전체 종합 등급 계산
     * 주요 4개 지표(CAGR, MDD, Sharpe, 승률)의 등급을 종합
     */
    fun calculateOverallGrade(
        cagr: BigDecimal,
        mdd: BigDecimal,
        sharpe: BigDecimal?,
        winRate: BigDecimal?
    ): MetricGrade {
        val grades = listOfNotNull(
            gradeCagr(cagr).grade,
            gradeMdd(mdd).grade,
            sharpe?.let { gradeSharpe(it).grade },
            winRate?.let { gradeWinRate(it).grade }
        )
        if (grades.isEmpty()) return MetricGrade.WARNING

        val dangerCount = grades.count { it == MetricGrade.DANGER }
        val goodCount = grades.count { it == MetricGrade.GOOD }

        return when {
            dangerCount >= 2 -> MetricGrade.DANGER
            goodCount > grades.size / 2 -> MetricGrade.GOOD
            else -> MetricGrade.WARNING
        }
    }

    /**
     * 초보자용 평문 요약 생성
     * 예: "1,000만원으로 시작해서 5년 후 1,523만원이 되었습니다. 연평균 8.8%의 수익률을 기록했습니다."
     */
    fun generatePlainSummary(result: BacktestResult): String {
        val formatter = NumberFormat.getNumberInstance(Locale.KOREA)
        val initialWon = formatKoreanMoney(result.initialCapital)
        val finalWon = formatKoreanMoney(result.finalValue)

        val years = java.time.temporal.ChronoUnit.YEARS.between(result.startDate, result.endDate)
        val periodText = if (years > 0) "${years}년" else {
            val months = java.time.temporal.ChronoUnit.MONTHS.between(result.startDate, result.endDate)
            "${months}개월"
        }

        val profitLoss = result.finalValue.subtract(result.initialCapital)
        val profitLossText = if (profitLoss >= BigDecimal.ZERO) {
            "${formatKoreanMoney(profitLoss)} 수익"
        } else {
            "${formatKoreanMoney(profitLoss.abs())} 손실"
        }

        val cagrText = formatPercent(result.cagr)
        val mddText = formatPercent(result.mdd.abs())

        val sb = StringBuilder()
        sb.append("${initialWon}으로 시작해서 $periodText 후 ${finalWon}이 되었습니다.")
        sb.append(" (${profitLossText})")
        sb.append("\n연평균 ${cagrText}%의 수익률을 기록했고,")
        sb.append(" 가장 크게 빠졌을 때 ${mddText}% 하락했습니다.")

        if (result.totalTrades > 0) {
            sb.append("\n총 ${formatter.format(result.totalTrades)}번 거래했으며,")
            result.winRate?.let {
                sb.append(" 그중 ${formatPercent(it)}%가 수익 거래였습니다.")
            }
        }

        return sb.toString()
    }

    /**
     * 한국 화폐 표시 (1000만원, 1억 2300만원 등)
     */
    private fun formatKoreanMoney(amount: BigDecimal): String {
        val value = amount.setScale(0, RoundingMode.HALF_UP).toLong()
        val absValue = kotlin.math.abs(value)

        return when {
            absValue >= 100_000_000 -> {
                val eok = absValue / 100_000_000
                val remainder = (absValue % 100_000_000) / 10_000
                if (remainder > 0) "${eok}억 ${NumberFormat.getNumberInstance(Locale.KOREA).format(remainder)}만원"
                else "${eok}억원"
            }
            absValue >= 10_000 -> {
                val man = absValue / 10_000
                val remainder = absValue % 10_000
                if (remainder > 0) "${NumberFormat.getNumberInstance(Locale.KOREA).format(man)}만 ${NumberFormat.getNumberInstance(Locale.KOREA).format(remainder)}원"
                else "${NumberFormat.getNumberInstance(Locale.KOREA).format(man)}만원"
            }
            else -> "${NumberFormat.getNumberInstance(Locale.KOREA).format(absValue)}원"
        }
    }

    private fun formatPercent(value: BigDecimal): String {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString()
    }

    private fun formatDecimal(value: BigDecimal): String {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }
}
