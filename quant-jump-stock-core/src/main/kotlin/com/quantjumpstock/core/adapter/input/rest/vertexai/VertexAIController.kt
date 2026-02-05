package com.quantjumpstock.core.adapter.input.rest.vertexai

import com.quantjumpstock.core.adapter.input.api.JobCallbackRequest
import com.quantjumpstock.core.adapter.input.api.VertexAIApi
import com.quantjumpstock.core.application.vertexai.VertexAIService
import com.quantjumpstock.core.domain.vertexai.model.JobCallback
import com.quantjumpstock.core.domain.vertexai.model.JobStatus
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Vertex AI Controller (Input Adapter)
 *
 * HTTP 요청을 VertexAIService로 전달하는 Adapter 역할만 수행.
 * 비즈니스 로직은 Application Service에서 처리.
 */
@RestController
@RequestMapping("/api/v1/vertex-ai")
@ConditionalOnProperty(name = ["gcp.enabled"], havingValue = "true", matchIfMissing = false)
class VertexAIController(
    private val vertexAIService: VertexAIService
) : VertexAIApi {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun runPrediction(): ResponseEntity<Map<String, Any>> {
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

    override fun getJobStatus(@org.springframework.web.bind.annotation.RequestParam jobId: String): ResponseEntity<Map<String, Any>> {
        val info = vertexAIService.getJobStatusInfo(jobId)

        return ResponseEntity.ok(mapOf(
            "success" to true,
            "jobId" to info.jobId,
            "message" to info.message,
            "dataEngineApi" to info.dataEngineApi
        ))
    }

    override fun cancelJob(jobId: String): ResponseEntity<Map<String, Any>> {
        val info = vertexAIService.getCancelJobInfo(jobId)

        return ResponseEntity.ok(mapOf(
            "success" to true,
            "jobId" to info.jobId,
            "message" to info.message,
            "dataEngineApi" to info.dataEngineApi
        ))
    }

    override fun jobCallback(request: JobCallbackRequest): ResponseEntity<Map<String, Any>> {
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
