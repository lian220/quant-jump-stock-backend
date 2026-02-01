package com.quantjumpstock.core.service

import com.google.cloud.aiplatform.v1.JobServiceClient
import com.google.cloud.aiplatform.v1.JobState
import com.quantjumpstock.core.adapter.output.notification.slack.SlackApiClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value as SpringValue
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Vertex AI CustomJob 모니터링 전용 서비스
 * @Async가 제대로 동작하도록 별도 클래스로 분리
 */
@Service
class VertexAIJobMonitor(
    private val jobServiceClient: JobServiceClient,
    private val slackApiClient: SlackApiClient,
    @SpringValue("\${gcp.vertex-ai.timeout}") private val timeout: Int,
    @SpringValue("\${gcp.vertex-ai.job-name}") private val jobName: String
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Job 완료 모니터링 (비동기)
     * @param threadTs Slack 스레드 타임스탬프 (답글용)
     */
    @Async
    fun monitorJobCompletionAsync(fullJobName: String, requestId: String, threadTs: String?) {
        logger.info("🔍 비동기 Job 모니터링 시작: $fullJobName (Thread: ${Thread.currentThread().name}, ThreadTs: $threadTs)")

        try {
            var currentState: JobState
            var elapsedSeconds = 0
            val checkIntervalSeconds = 30

            while (true) {
                Thread.sleep(checkIntervalSeconds * 1000L)
                elapsedSeconds += checkIntervalSeconds

                val job = jobServiceClient.getCustomJob(fullJobName)
                currentState = job.state

                logger.info("[${elapsedSeconds}초] Job 상태: $currentState")

                when (currentState) {
                    JobState.JOB_STATE_SUCCEEDED -> {
                        logger.info("✅ Job 성공적으로 완료: $fullJobName")
                        slackApiClient.notifyVertexAIJobCompleted(
                            requestId = requestId,
                            jobName = this.jobName,
                            duration = "${elapsedSeconds / 60}분",
                            status = "SUCCESS",
                            threadTs = threadTs
                        )
                        return
                    }
                    JobState.JOB_STATE_FAILED -> {
                        val errorMsg = job.error?.message ?: "Unknown error"
                        logger.error("❌ Job 실패: $errorMsg")
                        slackApiClient.notifyVertexAIJobFailed(requestId, this.jobName, errorMsg, threadTs)
                        return
                    }
                    JobState.JOB_STATE_CANCELLED -> {
                        logger.warn("⚠️ Job 취소됨")
                        slackApiClient.notifyVertexAIJobFailed(requestId, this.jobName, "Job cancelled", threadTs)
                        return
                    }
                    else -> {
                        // 계속 대기
                        if (elapsedSeconds > timeout) {
                            logger.error("❌ Job 타임아웃 (${timeout}초 초과)")
                            slackApiClient.notifyVertexAIJobFailed(requestId, this.jobName, "Timeout after ${timeout}s", threadTs)
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("❌ Job 모니터링 중 오류 발생", e)
            slackApiClient.notifyVertexAIJobFailed(requestId, this.jobName, "Monitoring error: ${e.message}", threadTs)
        }
    }
}
