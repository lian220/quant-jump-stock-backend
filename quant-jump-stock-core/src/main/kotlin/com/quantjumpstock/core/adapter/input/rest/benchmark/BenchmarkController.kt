package com.quantjumpstock.core.adapter.input.rest.benchmark

import com.quantjumpstock.core.application.benchmark.BenchmarkService
import com.quantjumpstock.core.domain.model.benchmark.BenchmarkSeries
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/benchmarks")
@Tag(name = "Benchmark", description = "벤치마크 시계열 API")
class BenchmarkController(
    private val benchmarkService: BenchmarkService
) {

    @GetMapping("/series")
    @Operation(summary = "벤치마크 시계열 조회", description = "지정 기간 동안의 벤치마크 시계열 데이터를 정규화하여 반환합니다.")
    fun getBenchmarkSeries(
        @Parameter(description = "벤치마크 ticker 목록 (콤마 구분, 예: SPY,QQQ)")
        @RequestParam tickers: String,

        @Parameter(description = "시작일 (yyyy-MM-dd)")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,

        @Parameter(description = "종료일 (yyyy-MM-dd)")
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,

        @Parameter(description = "초기 투자금 (정규화 기준, 기본값 10,000,000)")
        @RequestParam(required = false, defaultValue = "10000000") initialCapital: BigDecimal
    ): ResponseEntity<BenchmarkSeriesResponse> {
        val tickerList = tickers.split(",").map { it.trim() }.filter { it.isNotBlank() }

        if (tickerList.isEmpty()) {
            return ResponseEntity.badRequest().body(BenchmarkSeriesResponse(emptyList()))
        }

        if (tickerList.size > 10) {
            return ResponseEntity.badRequest().body(BenchmarkSeriesResponse(emptyList()))
        }

        val benchmarks = benchmarkService.getBenchmarkSeries(
            tickers = tickerList,
            startDate = startDate,
            endDate = endDate,
            initialCapital = initialCapital
        )

        return ResponseEntity.ok(BenchmarkSeriesResponse(benchmarks))
    }
}

data class BenchmarkSeriesResponse(
    val benchmarks: List<BenchmarkSeries>
)
