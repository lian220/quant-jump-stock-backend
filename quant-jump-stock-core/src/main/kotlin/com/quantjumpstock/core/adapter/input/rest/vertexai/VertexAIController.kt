package com.quantjumpstock.core.adapter.input.rest.vertexai

import com.google.cloud.aiplatform.v1.JobState
import com.quantjumpstock.core.adapter.input.api.JobCallbackRequest
import com.quantjumpstock.core.adapter.input.api.VertexAIApi
import com.quantjumpstock.core.adapter.output.gcp.GcpProperties
import com.quantjumpstock.core.adapter.output.gcp.vertexai.VertexAIJobService
import com.quantjumpstock.core.adapter.output.notification.slack.SlackApiClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Vertex AI 제어 Controller
 */
@RestController
@RequestMapping("/api/v1/vertex-ai")
@ConditionalOnProperty(name = ["gcp.enabled"], havingValue = "true", matchIfMissing = false)
class VertexAIController(
    private val vertexAIJobService: VertexAIJobService,
    private val slackApiClient: SlackApiClient,
    private val gcpProperties: GcpProperties
) : VertexAIApi {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun runPrediction(): ResponseEntity<Map<String, Any>> {
        return try {
            logger.info("🚀 Vertex AI 예측 수동 실행 요청")

            val jobId = vertexAIJobService.createAndRunCustomJob()

            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Vertex AI 예측 실행 완료",
                "jobId" to jobId,
                "estimatedTime" to "3-5분"
            ))
        } catch (e: Exception) {
            logger.error("❌ Vertex AI 예측 실행 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "Vertex AI 예측 실행 실패: ${e.message}"
            ))
        }
    }

    override fun getJobStatus(@org.springframework.web.bind.annotation.RequestParam jobId: String): ResponseEntity<Map<String, Any>> {
        return try {
            val state = vertexAIJobService.getJobState(jobId)

            ResponseEntity.ok(mapOf(
                "success" to true,
                "jobId" to jobId,
                "state" to state.name,
                "stateDescription" to getStateDescription(state)
            ))
        } catch (e: Exception) {
            logger.error("❌ Job 상태 조회 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "Job 상태 조회 실패: ${e.message}"
            ))
        }
    }

    override fun cancelJob(jobId: String): ResponseEntity<Map<String, Any>> {
        return try {
            vertexAIJobService.cancelJob(jobId)

            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Job 취소 요청 완료",
                "jobId" to jobId
            ))
        } catch (e: Exception) {
            logger.error("❌ Job 취소 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "Job 취소 실패: ${e.message}"
            ))
        }
    }

    /**
     * Job 완료 콜백 (predict_optimized.py에서 호출)
     */
    override fun jobCallback(request: JobCallbackRequest): ResponseEntity<Map<String, Any>> {
        logger.info("=".repeat(60))
        logger.info("📬 Vertex AI Job 콜백 수신")
        logger.info("Request ID: ${request.requestId}")
        logger.info("Status: ${request.status}")
        logger.info("Thread TS: ${request.threadTs}")
        logger.info("=".repeat(60))

        return try {
            when (request.status.uppercase()) {
                "SUCCESS" -> {
                    slackApiClient.notifyVertexAIJobCompleted(
                        requestId = request.requestId,
                        jobName = gcpProperties.vertexAi.jobName,
                        duration = request.duration ?: "unknown",
                        status = "SUCCESS",
                        threadTs = request.threadTs
                    )
                    logger.info("✅ 성공 알림 전송 완료")
                }
                "FAILED" -> {
                    slackApiClient.notifyVertexAIJobFailed(
                        requestId = request.requestId,
                        jobName = gcpProperties.vertexAi.jobName,
                        error = request.errorDetail ?: request.message ?: "Unknown error",
                        threadTs = request.threadTs
                    )
                    logger.info("❌ 실패 알림 전송 완료")
                }
                else -> {
                    logger.warn("⚠️ 알 수 없는 상태: ${request.status}")
                }
            }

            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "콜백 처리 완료",
                "requestId" to request.requestId,
                "status" to request.status
            ))
        } catch (e: Exception) {
            logger.error("❌ 콜백 처리 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "콜백 처리 실패: ${e.message}"
            ))
        }
    }

    /**
     * Job 상태 설명
     */
    private fun getStateDescription(state: JobState): String {
        return when (state) {
            JobState.JOB_STATE_QUEUED -> "대기 중"
            JobState.JOB_STATE_PENDING -> "준비 중"
            JobState.JOB_STATE_RUNNING -> "실행 중"
            JobState.JOB_STATE_SUCCEEDED -> "완료"
            JobState.JOB_STATE_FAILED -> "실패"
            JobState.JOB_STATE_CANCELLING -> "취소 중"
            JobState.JOB_STATE_CANCELLED -> "취소됨"
            JobState.JOB_STATE_PAUSED -> "일시정지"
            JobState.JOB_STATE_EXPIRED -> "만료됨"
            else -> "알 수 없음"
        }
    }
}
