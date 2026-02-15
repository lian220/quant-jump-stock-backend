package com.quantjumpstock.core.adapter.input.rest.admin

import com.quantjumpstock.core.application.vertexai.VertexAIService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import com.quantjumpstock.core.adapter.input.api.StandardApiResponses
import com.quantjumpstock.core.adapter.input.api.VertexAIJobResponses
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Vertex AI Controller (Admin 전용 Input Adapter)
 *
 * 예측 실행, 상태 조회, 취소 등 Admin 전용 엔드포인트.
 * 콜백 엔드포인트는 VertexAICallbackController에서 처리합니다.
 */
@Tag(name = "Admin - Vertex AI", description = "Google Vertex AI 예측 모델 관리 (Admin 전용)")
@RestController
@RequestMapping("/api/v1/admin/vertex-ai")
@ConditionalOnProperty(name = ["gcp.enabled"], havingValue = "true", matchIfMissing = false)
class AdminVertexAIController(
    private val vertexAIService: VertexAIService
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostMapping("/predict")
    @Operation(
        summary = "Vertex AI 예측 수동 실행",
        description = "스케줄러 대기 없이 즉시 Vertex AI CustomJob 실행"
    )
    @VertexAIJobResponses
    fun runPrediction(): ResponseEntity<Map<String, Any>> {
        return try {
            val result = vertexAIService.runPrediction()

            ResponseEntity.ok(mapOf(
                "success" to result.success,
                "message" to result.message,
                "requestId" to result.requestId,
                "threadTs" to (result.threadTs ?: ""),
                "estimatedTime" to "3-5분",
                "note" to "실제 실행은 Data Engine에서 처리됩니다"
            ))
        } catch (e: Exception) {
            logger.error("❌ Vertex AI 예측 요청 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "Vertex AI 예측 요청 실패: ${e.message}"
            ))
        }
    }

    @GetMapping("/jobs/status")
    @Operation(summary = "Vertex AI Job 상태 조회", description = "실행 중인 Job의 현재 상태 확인. jobId는 URL 인코딩된 전체 리소스 이름")
    @StandardApiResponses
    fun getJobStatus(@RequestParam jobId: String): ResponseEntity<Map<String, Any>> {
        val info = vertexAIService.getJobStatusInfo(jobId)

        return ResponseEntity.ok(mapOf(
            "success" to true,
            "jobId" to info.jobId,
            "message" to info.message,
            "dataEngineApi" to info.dataEngineApi
        ))
    }

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(summary = "Vertex AI Job 취소", description = "실행 중인 Job 강제 취소")
    @StandardApiResponses
    fun cancelJob(@PathVariable jobId: String): ResponseEntity<Map<String, Any>> {
        val info = vertexAIService.getCancelJobInfo(jobId)

        return ResponseEntity.ok(mapOf(
            "success" to true,
            "jobId" to info.jobId,
            "message" to info.message,
            "dataEngineApi" to info.dataEngineApi
        ))
    }
}
