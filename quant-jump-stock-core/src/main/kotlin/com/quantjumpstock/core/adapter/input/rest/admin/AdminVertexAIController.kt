package com.quantjumpstock.core.adapter.input.rest.admin

import com.quantjumpstock.core.application.vertexai.VertexAIService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import com.quantjumpstock.core.adapter.input.api.StandardApiResponses
import com.quantjumpstock.core.adapter.input.api.VertexAIJobResponses
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Vertex AI Controller (Admin 전용 Input Adapter)
 *
 * 정상 운영: Cloud Scheduler → Vertex AI API 직접 호출 (Core API 경유 안 함)
 * 긴급 수동: 이 컨트롤러를 통해 Pub/Sub → Data Engine → Vertex AI 실행
 *
 * 콜백 엔드포인트는 VertexAICallbackController에서 처리합니다.
 */
@Tag(name = "Admin - Vertex AI", description = "Google Vertex AI 예측 모델 관리 (Admin 전용)")
@RestController
@RequestMapping("/api/v1/admin/vertex-ai")
class AdminVertexAIController(
    private val vertexAIService: VertexAIService
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostMapping("/predict")
    @Operation(
        summary = "Vertex AI 예측 긴급 수동 실행",
        description = "정상 운영에서는 Cloud Scheduler가 직접 Vertex AI를 호출합니다. " +
            "이 엔드포인트는 긴급 수동 실행용이며, Pub/Sub → Data Engine 경로로 실행됩니다. " +
            "fineTune=true(기본): Fine-tuning, fineTune=false: Full Training"
    )
    @VertexAIJobResponses
    fun runPrediction(
        @RequestBody(required = false) body: PredictRequest?
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val fineTune = body?.fineTune ?: true
            val result = vertexAIService.runPrediction(fineTune = fineTune)

            ResponseEntity.ok(mapOf(
                "success" to result.success,
                "message" to result.message,
                "requestId" to result.requestId,
                "threadTs" to (result.threadTs ?: ""),
                "mode" to if (fineTune) "fine-tuning" else "full-training",
                "estimatedTime" to if (fineTune) "3-5분" else "20-30분",
                "note" to "실제 실행은 Data Engine에서 처리됩니다 (긴급 수동 실행)"
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
        return try {
            val info = vertexAIService.getJobStatusInfo(jobId)

            ResponseEntity.ok(mapOf(
                "success" to true,
                "jobId" to info.jobId,
                "message" to info.message,
                "dataEngineApi" to info.dataEngineApi
            ))
        } catch (e: Exception) {
            logger.error("❌ Vertex AI Job 상태 조회 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "Job 상태 조회 실패: ${e.message}"
            ))
        }
    }

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(summary = "Vertex AI Job 취소", description = "실행 중인 Job 강제 취소")
    @StandardApiResponses
    fun cancelJob(@PathVariable jobId: String): ResponseEntity<Map<String, Any>> {
        return try {
            val info = vertexAIService.getCancelJobInfo(jobId)

            ResponseEntity.ok(mapOf(
                "success" to true,
                "jobId" to info.jobId,
                "message" to info.message,
                "dataEngineApi" to info.dataEngineApi
            ))
        } catch (e: Exception) {
            logger.error("❌ Vertex AI Job 취소 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "Job 취소 실패: ${e.message}"
            ))
        }
    }
}

/**
 * 예측 실행 요청 DTO
 */
data class PredictRequest(
    val fineTune: Boolean = true
)
