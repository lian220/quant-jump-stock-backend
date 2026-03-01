package com.quantjumpstock.core.adapter.output.notification

import com.quantjumpstock.core.adapter.output.notification.slack.SlackMessage
import com.quantjumpstock.core.domain.news.port.output.NewsAlertSender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.concurrent.CompletableFuture

@Component
class NewsSlackAlertAdapter(
    private val restClient: RestClient,
    @Value("\${slack.enabled:true}") private val slackEnabled: Boolean,
    @Value("\${slack.webhook-url-news:}") private val slackWebhookUrl: String
) : NewsAlertSender {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun sendAlert(message: String) {
        if (!slackEnabled) return
        if (slackWebhookUrl.isBlank()) return
        CompletableFuture.runAsync {
            try {
                restClient.post()
                    .uri(slackWebhookUrl)
                    .body(SlackMessage(text = message))
                    .retrieve()
                    .body(String::class.java)
            } catch (e: Exception) {
                logger.warn("Slack 뉴스 알림 발송 실패: {}", e.message)
            }
        }
    }
}
