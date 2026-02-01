package com.quantjumpstock.core.adapter.input.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

/**
 * Vertex AI 제어 API 스펙
 */
@Tag(name = "Vertex AI", description = "Google Vertex AI 예측 모델 관리")
interface VertexAIApi {

    @PostMapping("/predict")
    @Operation(summary = "Vertex AI 예측 수동 실행", description = "스케줄러 대기 없이 즉시 Vertex AI CustomJob 실행")
    @VertexAIJobResponses
    fun runPrediction(): ResponseEntity<Map<String, Any>>

    @GetMapping("/jobs/{jobId}/status")
    @Operation(summary = "Vertex AI Job 상태 조회", description = "실행 중인 Job의 현재 상태 확인")
    @StandardApiResponses
    fun getJobStatus(@PathVariable jobId: String): ResponseEntity<Map<String, Any>>

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(summary = "Vertex AI Job 취소", description = "실행 중인 Job 강제 취소")
    @StandardApiResponses
    fun cancelJob(@PathVariable jobId: String): ResponseEntity<Map<String, Any>>
}
