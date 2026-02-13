package com.quantjumpstock.core.adapter.input.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate

/**
 * 예측 결과 조회 API 스펙
 */
@Tag(name = "Predictions", description = "Vertex AI 예측 결과 조회")
interface PredictionApi {

    @GetMapping
    @Operation(summary = "전체 예측 결과 조회", description = "지정된 날짜 이후의 모든 예측 결과 조회")
    @PredictionQueryResponses
    fun getAllPredictions(
        @RequestParam(defaultValue = "7")
        @Parameter(description = "조회할 일수 (기본 7일)")
        days: Int
    ): ResponseEntity<Map<String, Any>>

    @GetMapping("/latest")
    @Operation(summary = "최신 예측 결과 조회", description = "오늘 날짜의 모든 예측 결과")
    @PredictionQueryResponses
    fun getLatestPredictions(): ResponseEntity<Map<String, Any>>

    @GetMapping("/buy-signals")
    @Operation(summary = "매수 신호 조회", description = "지정된 날짜(또는 최신 데이터)의 매수 신호 조회 (점수 높은 순)")
    @PredictionQueryResponses
    fun getBuySignals(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "분석 날짜 (YYYY-MM-DD, 미지정 시 최신 데이터 날짜 사용)")
        date: LocalDate?,

        @RequestParam(required = false)
        @Parameter(description = "최소 Composite Score 비율 (0.0 ~ 1.0, 기본값 0.0). Score × 7.5로 변환됨")
        minConfidence: Double?
    ): ResponseEntity<Map<String, Any>>

    @GetMapping("/{symbol}")
    @Operation(summary = "특정 종목 예측 조회", description = "특정 종목의 최근 예측 결과 (날짜 내림차순)")
    @PredictionQueryResponses
    fun getPredictionsBySymbol(
        @PathVariable symbol: String,
        @RequestParam(defaultValue = "10")
        @Parameter(description = "조회할 개수 (기본 10개)")
        limit: Int
    ): ResponseEntity<Map<String, Any>>

    @GetMapping("/date/{date}")
    @Operation(summary = "특정 날짜 예측 조회", description = "지정된 날짜의 모든 예측 결과")
    @PredictionQueryResponses
    fun getPredictionsByDate(
        @PathVariable
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "날짜 (YYYY-MM-DD)")
        date: LocalDate
    ): ResponseEntity<Map<String, Any>>

    @GetMapping("/stats")
    @Operation(summary = "예측 통계", description = "최근 예측 결과 통계 (신뢰도 분포, 신호 비율 등)")
    @PredictionQueryResponses
    fun getPredictionStats(
        @RequestParam(defaultValue = "7")
        @Parameter(description = "통계 기간 (일수)")
        days: Int
    ): ResponseEntity<Map<String, Any>>
}
