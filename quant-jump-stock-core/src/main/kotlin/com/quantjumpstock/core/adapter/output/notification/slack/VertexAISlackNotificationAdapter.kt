package com.quantjumpstock.core.adapter.output.notification.slack

import com.quantjumpstock.core.domain.vertexai.port.output.VertexAINotificationPort
import org.springframework.stereotype.Component

/**
 * Vertex AI Slack 알림 Adapter (Output Adapter)
 *
 * VertexAINotificationPort를 구현하여 Slack으로 알림 전송.
 */
@Component
class VertexAISlackNotificationAdapter(
    private val slackApiClient: SlackApiClient
) : VertexAINotificationPort {

    override fun notifyJobStarted(requestId: String, jobName: String): String? {
        return slackApiClient.notifyVertexAIJobStarted(requestId, jobName)
    }

    override fun notifyJobCompleted(
        requestId: String,
        jobName: String,
        duration: String,
        status: String,
        threadTs: String?
    ) {
        slackApiClient.notifyVertexAIJobCompleted(requestId, jobName, duration, status, threadTs)
    }

    override fun notifyJobFailed(
        requestId: String,
        jobName: String,
        error: String,
        threadTs: String?
    ) {
        slackApiClient.notifyVertexAIJobFailed(requestId, jobName, error, threadTs)
    }
}
