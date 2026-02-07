package com.quantjumpstock.core.domain.model.benchmark

import java.math.BigDecimal
import java.time.LocalDate

data class BenchmarkSeriesPoint(
    val date: LocalDate,
    val value: BigDecimal
)

data class BenchmarkSeries(
    val ticker: String,
    val displayName: String,
    val type: String,
    val points: List<BenchmarkSeriesPoint>
)
