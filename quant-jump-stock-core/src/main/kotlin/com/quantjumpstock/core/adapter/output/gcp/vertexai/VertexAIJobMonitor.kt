package com.quantjumpstock.core.adapter.output.gcp.vertexai

import com.google.cloud.aiplatform.v1.JobServiceClient
import com.google.cloud.aiplatform.v1.JobState
import com.quantjumpstock.core.adapter.output.gcp.GcpProperties
import com.quantjumpstock.core.adapter.output.notification.slack.SlackApiClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Vertex AI CustomJob 모니터링 서비스
 * @Async가 제대로 동작하도록 별도 클래스로 분리
 */
@Service
@ConditionalOnProperty(name = ["gcp.enabled"], havingValue = "true", matchIfMissing = false)
class VertexAIJobMonitor(
    private val jobServiceClient: JobServiceClient,
    private val slackApiClient: SlackApiClient,
    private val gcpProperties: GcpProperties
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val timeout: Int
        get() = gcpProperties.vertexAi.timeout

    private val jobName: String
        get() = gcpProperties.vertexAi.jobName

    /**
     * Job 완료 모니터링 (비동기)
     *
     * @param fullJobName Vertex AI Job 전체 리소스 이름
     * @param requestId 요청 ID
     * @param threadTs Slack 스레드 타임스탬프 (답글용)
     */
    @Async
    fun monitorJobCompletionAsync(fullJobName: String, requestId: String, threadTs: String?) {
        logger.info("🔍 비동기 Job 모니터링 시작: $fullJobName")
        logger.info("   Thread: ${Thread.currentThread().name}")
        logger.info("   ThreadTs: $threadTs")

        try {
            var elapsedSeconds = 0
            val checkIntervalSeconds = 30

            while (true) {
                Thread.sleep(checkIntervalSeconds * 1000L)
                elapsedSeconds += checkIntervalSeconds

                val job = jobServiceClient.getCustomJob(fullJobName)
                val currentState = job.state

                logger.info("[${elapsedSeconds}초] Job 상태: $currentState")

                when (currentState) {
                    JobState.JOB_STATE_SUCCEEDED -> {
                        logger.info("✅ Job 성공적으로 완료: $fullJobName")
                        slackApiClient.notifyVertexAIJobCompleted(
                            requestId = requestId,
                            jobName = jobName,
                            duration = "${elapsedSeconds / 60}분",
                            status = "SUCCESS",
                            threadTs = threadTs
                        )
                        return
                    }

                    JobState.JOB_STATE_FAILED -> {
                        val errorMsg = job.error?.message ?: "Unknown error"
                        logger.error("❌ Job 실패: $errorMsg")
                        slackApiClient.notifyVertexAIJobFailed(requestId, jobName, errorMsg, threadTs)
                        return
                    }

                    JobState.JOB_STATE_CANCELLED -> {
                        logger.warn("⚠️ Job 취소됨")
                        slackApiClient.notifyVertexAIJobFailed(requestId, jobName, "Job cancelled", threadTs)
                        return
                    }

                    else -> {
                        if (elapsedSeconds > timeout) {
                            logger.error("❌ Job 타임아웃 (${timeout}초 초과)")
                            slackApiClient.notifyVertexAIJobFailed(
                                requestId,
                                jobName,
                                "Timeout after ${timeout}s",
                                threadTs
                            )
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("❌ Job 모니터링 중 오류 발생", e)
            slackApiClient.notifyVertexAIJobFailed(
                requestId,
                jobName,
                "Monitoring error: ${e.message}",
                threadTs
            )
        }
    }
}
