package com.quantjumpstock.core.application.vertexai

import com.quantjumpstock.core.config.VertexAIConfig
import com.quantjumpstock.core.domain.economic.port.output.MessagePublisher
import com.quantjumpstock.core.domain.model.VertexAIPredictionRequest
import com.quantjumpstock.core.domain.vertexai.model.JobCallback
import com.quantjumpstock.core.domain.vertexai.model.JobStatus
import com.quantjumpstock.core.domain.vertexai.port.output.VertexAINotificationPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Vertex AI Application Service
 *
 * Vertex AI 예측 요청 및 콜백 처리를 담당하는 Application Service.
 * Controller에서 비즈니스 로직을 분리하여 Hexagonal Architecture 준수.
 */
@Service
class VertexAIService(
    private val messagePublisher: MessagePublisher,
    private val notificationPort: VertexAINotificationPort,
    private val vertexAIConfig: VertexAIConfig
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        const val VERTEX_AI_RUN_TOPIC = "vertex.ai.run.request"
    }

    /**
     * Vertex AI 예측 실행 요청
     *
     * @return 예측 요청 결과 (requestId, threadTs 포함)
     */
    fun runPrediction(): VertexAIPredictionResult {
        val requestId = UUID.randomUUID().toString()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        logger.info("🚀 Vertex AI 예측 요청 (Pub/Sub 발행)")
        logger.info("Request ID: $requestId")

        // 알림 시작 → threadTs 획득
        val threadTs = notificationPort.notifyJobStarted(requestId, vertexAIConfig.jobName)

        // Pub/Sub 메시지 발행 (Data Engine에서 Vertex AI 실행)
        val request = VertexAIPredictionRequest(
            timestamp = timestamp,
            source = "core-api",
            requestId = requestId,
            threadTs = threadTs,
            envVars = emptyMap()
        )

        messagePublisher.publishVertexAIPredictionRequest(VERTEX_AI_RUN_TOPIC, request)

        logger.info("✅ Pub/Sub 메시지 발행 완료: topic=$VERTEX_AI_RUN_TOPIC")

        return VertexAIPredictionResult(
            success = true,
            requestId = requestId,
            threadTs = threadTs,
            message = "Vertex AI 예측 요청이 전송되었습니다 (Pub/Sub)"
        )
    }

    /**
     * Job 완료 콜백 처리
     *
     * @param callback 콜백 도메인 모델
     * @return 콜백 처리 결과
     */
    fun handleJobCallback(callback: JobCallback): JobCallbackResult {
        logger.info("=".repeat(60))
        logger.info("📬 Vertex AI Job 콜백 수신")
        logger.info("Request ID: ${callback.requestId}")
        logger.info("Status: ${callback.status}")
        logger.info("Thread TS: ${callback.threadTs}")
        logger.info("=".repeat(60))

        when (callback.status) {
            JobStatus.SUCCESS -> {
                notificationPort.notifyJobCompleted(
                    requestId = callback.requestId,
                    jobName = vertexAIConfig.jobName,
                    duration = callback.duration ?: "unknown",
                    status = "SUCCESS",
                    threadTs = callback.threadTs
                )
                logger.info("✅ 성공 알림 전송 완료")
            }
            JobStatus.FAILED -> {
                notificationPort.notifyJobFailed(
                    requestId = callback.requestId,
                    jobName = vertexAIConfig.jobName,
                    error = callback.errorDetail ?: callback.message ?: "Unknown error",
                    threadTs = callback.threadTs
                )
                logger.info("❌ 실패 알림 전송 완료")
            }
        }

        return JobCallbackResult(
            success = true,
            requestId = callback.requestId,
            status = callback.status.name,
            message = "콜백 처리 완료"
        )
    }

    /**
     * Job 상태 조회 안내
     *
     * @param jobId Job ID
     * @return 안내 정보
     */
    fun getJobStatusInfo(jobId: String): JobStatusInfo {
        logger.info("📋 Job 상태 조회 요청: $jobId (Data Engine으로 프록시 필요)")

        return JobStatusInfo(
            jobId = jobId,
            message = "Job 상태 조회는 Data Engine API를 통해 확인하세요",
            dataEngineApi = "GET /api/v1/ml/job/status?job_name=$jobId"
        )
    }

    /**
     * Job 취소 안내
     *
     * @param jobId Job ID
     * @return 안내 정보
     */
    fun getCancelJobInfo(jobId: String): JobStatusInfo {
        logger.info("🛑 Job 취소 요청: $jobId (Data Engine으로 프록시 필요)")

        return JobStatusInfo(
            jobId = jobId,
            message = "Job 취소는 Data Engine API를 통해 요청하세요",
            dataEngineApi = "POST /api/v1/ml/job/cancel"
        )
    }
}

/**
 * 예측 요청 결과
 */
data class VertexAIPredictionResult(
    val success: Boolean,
    val requestId: String,
    val threadTs: String?,
    val message: String
)

/**
 * 콜백 처리 결과
 */
data class JobCallbackResult(
    val success: Boolean,
    val requestId: String,
    val status: String,
    val message: String
)

/**
 * Job 상태/취소 안내 정보
 */
data class JobStatusInfo(
    val jobId: String,
    val message: String,
    val dataEngineApi: String
)
