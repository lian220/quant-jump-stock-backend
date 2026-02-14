package com.quantjumpstock.core.adapter.output.notification

import com.quantjumpstock.core.domain.economic.port.output.NotificationSender
import com.quantjumpstock.core.adapter.output.notification.slack.SlackApiClient
import org.springframework.stereotype.Component

/**
 * Slack Notification Adapter (Output Adapter)
 * NotificationSender 인터페이스를 구현하여 Slack과 연동합니다.
 */
@Component
class SlackNotificationAdapter(
    private val slackApiClient: SlackApiClient
) : NotificationSender {

    override fun notifyEconomicDataUpdateRequest(requestId: String, startDate: String?, endDate: String?): String? {
        return slackApiClient.notifyEconomicDataUpdateRequest(requestId, startDate, endDate)
    }

    override fun notifyEconomicDataCollectionError(requestId: String, error: String) {
        slackApiClient.notifyEconomicDataCollectionError(requestId, error)
    }

    override fun notifyTechnicalAnalysisRequest(requestId: String, startDate: String?, endDate: String?): String? {
        return slackApiClient.notifyTechnicalAnalysisRequest(requestId, startDate, endDate)
    }

    override fun notifySentimentAnalysisRequest(requestId: String, startDate: String?, endDate: String?): String? {
        return slackApiClient.notifySentimentAnalysisRequest(requestId, startDate, endDate)
    }

    override fun notifyStockRecommendationRequest(requestId: String, startDate: String?, endDate: String?): String? {
        return slackApiClient.notifyStockRecommendationRequest(requestId, startDate, endDate)
    }

    override fun notifyAnalysisError(requestId: String, analysisType: String, error: String) {
        slackApiClient.notifyAnalysisError(requestId, analysisType, error)
    }
}
