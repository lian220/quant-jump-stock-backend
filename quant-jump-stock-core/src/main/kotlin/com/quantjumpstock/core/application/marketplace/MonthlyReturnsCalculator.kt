package com.quantjumpstock.core.application.marketplace

import com.quantjumpstock.core.domain.model.marketplace.EquityCurvePoint
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

/**
 * equity curve 데이터에서 월별 수익률을 계산하는 컴포넌트
 *
 * 각 월의 마지막 포인트 기준으로 이전 월 대비 수익률을 계산합니다.
 * 첫 월은 기준점이므로 결과에 포함하지 않습니다.
 */
@Component
class MonthlyReturnsCalculator {

    fun calculate(equityCurve: List<EquityCurvePoint>): List<MonthlyReturnDto> {
        if (equityCurve.size < 2) return emptyList()

        // YearMonth 기준으로 그룹핑하여 각 월의 마지막 값 추출
        val monthlyLastValues = equityCurve
            .groupBy { YearMonth.from(it.date) }
            .toSortedMap()
            .mapValues { (_, points) -> points.maxByOrNull { it.date }!!.value }

        val entries = monthlyLastValues.entries.toList()
        if (entries.size < 2) return emptyList()

        // 이전 월 대비 수익률 계산 (첫 월은 기준점이므로 제외)
        return entries.zipWithNext().mapNotNull { (prev, curr) ->
            if (prev.value.compareTo(BigDecimal.ZERO) == 0) return@mapNotNull null

            val returnPct = curr.value
                .subtract(prev.value)
                .divide(prev.value, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))
                .setScale(2, RoundingMode.HALF_UP)

            MonthlyReturnDto(
                year = curr.key.year,
                month = curr.key.monthValue,
                returnPct = returnPct
            )
        }
    }
}
