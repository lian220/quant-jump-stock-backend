package com.quantjumpstock.core.adapter.input.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

/**
 * Vertex AI 제어 API 스펙
 */
@Tag(name = "Vertex AI", description = "Google Vertex AI 예측 모델 관리")
interface VertexAIApi {

    @PostMapping("/predict")
    @Operation(
        summary = "Vertex AI 예측 수동 실행",
        description = "스케줄러 대기 없이 즉시 Vertex AI CustomJob 실행"
    )
    @VertexAIJobResponses
    fun runPrediction(): ResponseEntity<Map<String, Any>>

    @GetMapping("/jobs/status")
    @Operation(summary = "Vertex AI Job 상태 조회", description = "실행 중인 Job의 현재 상태 확인. jobId는 URL 인코딩된 전체 리소스 이름")
    @StandardApiResponses
    fun getJobStatus(@org.springframework.web.bind.annotation.RequestParam jobId: String): ResponseEntity<Map<String, Any>>

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(summary = "Vertex AI Job 취소", description = "실행 중인 Job 강제 취소")
    @StandardApiResponses
    fun cancelJob(@PathVariable jobId: String): ResponseEntity<Map<String, Any>>

    @PostMapping("/callback")
    @Operation(
        summary = "Vertex AI Job 완료 콜백",
        description = "predict_optimized.py에서 작업 완료 시 호출하는 콜백 API. Slack 알림 전송."
    )
    @StandardApiResponses
    fun jobCallback(@RequestBody request: JobCallbackRequest): ResponseEntity<Map<String, Any>>
}

/**
 * Job 콜백 요청 DTO
 */
data class JobCallbackRequest(
    val requestId: String,
    val status: String,  // SUCCESS, FAILED
    val message: String? = null,
    val threadTs: String? = null,
    val duration: String? = null,
    val predictedCount: Int? = null,
    val errorDetail: String? = null
)
