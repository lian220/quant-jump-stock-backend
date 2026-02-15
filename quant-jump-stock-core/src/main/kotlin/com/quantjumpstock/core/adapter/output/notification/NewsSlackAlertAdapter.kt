package com.quantjumpstock.core.adapter.output.notification

import com.quantjumpstock.core.adapter.output.notification.slack.SlackMessage
import com.quantjumpstock.core.domain.news.port.output.NewsAlertSender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class NewsSlackAlertAdapter(
    private val webClient: WebClient,
    @Value("\${slack.webhook-url:}") private val slackWebhookUrl: String
) : NewsAlertSender {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun sendAlert(message: String) {
        if (slackWebhookUrl.isBlank()) return
        try {
            webClient.post()
                .uri(slackWebhookUrl)
                .bodyValue(SlackMessage(text = message, attachments = emptyList()))
                .retrieve()
                .bodyToMono(String::class.java)
                .subscribe(
                    { },
                    { e -> logger.warn("Slack 뉴스 알림 발송 실패: {}", e.message) }
                )
        } catch (e: Exception) {
            logger.warn("Slack 뉴스 알림 발송 실패: {}", e.message)
        }
    }
}
