package com.quantjumpstock.core.application.benchmark

import com.quantjumpstock.core.domain.model.benchmark.BenchmarkSeries
import com.quantjumpstock.core.domain.model.benchmark.BenchmarkSeriesPoint
import com.quantjumpstock.core.domain.port.output.Benchmark
import com.quantjumpstock.core.domain.port.output.BenchmarkDataPort
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

@Service
class BenchmarkService(
    private val benchmarkDataPort: BenchmarkDataPort
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Cacheable(
        value = ["benchmark"],
        key = "#tickers.toString() + '_' + #startDate + '_' + #endDate + '_' + #initialCapital"
    )
    fun getBenchmarkSeries(
        tickers: List<String>,
        startDate: LocalDate,
        endDate: LocalDate,
        initialCapital: BigDecimal
    ): List<BenchmarkSeries> {
        return tickers.mapNotNull { ticker ->
            val benchmark = Benchmark.findByTicker(ticker)
            if (benchmark == null) {
                log.warn("지원되지 않는 벤치마크 ticker: {}", ticker)
                return@mapNotNull null
            }

            val rawPoints = benchmarkDataPort.loadBenchmarkSeries(
                ticker = benchmark.ticker,
                type = benchmark.type,
                startDate = startDate,
                endDate = endDate
            )

            if (rawPoints.isEmpty()) {
                log.warn("벤치마크 데이터 없음: {} ({} ~ {})", ticker, startDate, endDate)
                return@mapNotNull null
            }

            val normalizedPoints = normalize(rawPoints, initialCapital)

            BenchmarkSeries(
                ticker = benchmark.ticker,
                displayName = benchmark.displayName,
                type = benchmark.type.value,
                points = normalizedPoints
            )
        }
    }

    /**
     * 정규화: initialCapital × (currentClose / firstClose)
     */
    private fun normalize(
        points: List<BenchmarkSeriesPoint>,
        initialCapital: BigDecimal
    ): List<BenchmarkSeriesPoint> {
        if (points.isEmpty()) return emptyList()

        val firstClose = points.first().value
        if (firstClose.compareTo(BigDecimal.ZERO) == 0) return emptyList()

        return points.map { point ->
            val normalized = initialCapital.multiply(point.value)
                .divide(firstClose, MathContext.DECIMAL64)
                .setScale(0, RoundingMode.HALF_UP)
            BenchmarkSeriesPoint(date = point.date, value = normalized)
        }
    }
}
