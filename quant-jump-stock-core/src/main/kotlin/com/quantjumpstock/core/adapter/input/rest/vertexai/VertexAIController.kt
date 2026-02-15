package com.quantjumpstock.core.adapter.input.rest.vertexai

import com.quantjumpstock.core.adapter.input.api.JobCallbackRequest
import com.quantjumpstock.core.adapter.input.api.StandardApiResponses
import com.quantjumpstock.core.application.vertexai.VertexAIService
import com.quantjumpstock.core.domain.vertexai.model.JobCallback
import com.quantjumpstock.core.domain.vertexai.model.JobStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Vertex AI 콜백 Controller
 *
 * GCP 내부에서 호출하는 콜백 엔드포인트만 처리합니다.
 * 이 경로는 SecurityConfig에서 permitAll로 설정되어 인증 없이 접근 가능합니다.
 * Admin 전용 엔드포인트(예측 실행, 상태 조회, 취소)는 AdminVertexAIController에서 처리합니다.
 */
@Tag(name = "Vertex AI Callback", description = "Vertex AI Job 콜백 (내부 전용)")
@RestController
@RequestMapping("/api/v1/vertex-ai")
@ConditionalOnProperty(name = ["gcp.enabled"], havingValue = "true", matchIfMissing = false)
class VertexAICallbackController(
    private val vertexAIService: VertexAIService
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostMapping("/callback")
    @Operation(
        summary = "Vertex AI Job 완료 콜백",
        description = "predict_optimized.py에서 작업 완료 시 호출하는 콜백 API. Slack 알림 전송."
    )
    @StandardApiResponses
    fun jobCallback(@RequestBody request: JobCallbackRequest): ResponseEntity<Map<String, Any>> {
        return try {
            // DTO → 도메인 모델 변환
            val callback = JobCallback(
                requestId = request.requestId,
                status = JobStatus.fromString(request.status),
                message = request.message,
                threadTs = request.threadTs,
                duration = request.duration,
                predictedCount = request.predictedCount,
                errorDetail = request.errorDetail
            )

            val result = vertexAIService.handleJobCallback(callback)

            ResponseEntity.ok(mapOf(
                "success" to result.success,
                "message" to result.message,
                "requestId" to result.requestId,
                "status" to result.status
            ))
        } catch (e: Exception) {
            logger.error("❌ 콜백 처리 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "콜백 처리 실패: ${e.message}"
            ))
        }
    }
}
