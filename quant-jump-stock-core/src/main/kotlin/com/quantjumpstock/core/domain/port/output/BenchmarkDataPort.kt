package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.benchmark.BenchmarkSeriesPoint
import java.time.LocalDate

interface BenchmarkDataPort {
    fun loadBenchmarkSeries(
        ticker: String,
        type: BenchmarkType,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<BenchmarkSeriesPoint>
}
