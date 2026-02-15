package com.quantjumpstock.core.application.news

import com.quantjumpstock.core.domain.news.model.NewsSource
import com.quantjumpstock.core.domain.news.port.input.NewsCollectionUseCase
import com.quantjumpstock.core.domain.news.port.output.CollectorStateRepository
import com.quantjumpstock.core.domain.news.port.output.NewsCollector
import com.quantjumpstock.core.domain.news.port.output.NewsRepository
import com.quantjumpstock.core.adapter.output.notification.slack.SlackApiClient
import com.quantjumpstock.core.adapter.output.notification.slack.SlackAttachment
import com.quantjumpstock.core.adapter.output.notification.slack.SlackField
import com.quantjumpstock.core.adapter.output.notification.slack.SlackMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDateTime

@Service
class NewsCollectionService(
    private val collectors: List<NewsCollector>,
    private val newsRepository: NewsRepository,
    private val collectorStateRepository: CollectorStateRepository,
    private val scorer: NewsScorer,
    private val subscriptionService: NewsSubscriptionService,
    private val webClient: WebClient,
    @Value("\${slack.webhook-url:}") private val slackWebhookUrl: String
) : NewsCollectionUseCase {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun collectAll() {
        collectors.forEach { collector ->
            try {
                collectFrom(collector)
            } catch (e: Exception) {
                logger.error("${collector.source} 수집 실패", e)
                collectorStateRepository.recordError(collector.source, e.message ?: "Unknown")
            }
        }
    }

    override fun collectFrom(source: NewsSource) {
        val collector = collectors.find { it.source == source }
            ?: throw IllegalArgumentException("Collector not found for source: $source")
        collectFrom(collector)
    }

    private fun collectFrom(collector: NewsCollector) {
        val items = collector.collect()
        if (items.isEmpty()) return

        val scored = items.map { scorer.score(it) }

        newsRepository.saveAll(scored.map { it.newsItem })

        // 구독자 매칭 → 인앱 알림 생성
        scored.forEach { scoredItem ->
            try {
                subscriptionService.createNotificationsForNews(
                    newsId = scoredItem.newsItem.externalId,
                    title = scoredItem.newsItem.titleKo,
                    summary = scoredItem.newsItem.contentKo?.take(200),
                    categories = scoredItem.newsItem.tags,
                    tickers = scoredItem.newsItem.tickers,
                    sourceName = scoredItem.newsItem.source.name,
                    importance = scoredItem.score,
                    sourceUrl = scoredItem.newsItem.sourceUrl
                )
            } catch (e: Exception) {
                logger.debug("알림 생성 실패: {}", e.message)
            }
        }

        // Slack 알림 (중요 뉴스만)
        scored.filter { it.score >= 0.4 }.forEach { scoredItem ->
            val emoji = if (scoredItem.score >= 0.7) "🚨" else "📰"
            val rumorLabel = if (scoredItem.isRumor) " [미확인]" else ""
            sendSlackNotification(
                "$emoji [${scoredItem.newsItem.source}]$rumorLabel ${scoredItem.newsItem.titleKo}\n" +
                    "점수: ${"%.2f".format(scoredItem.score)} | ${scoredItem.reasons.joinToString(", ")}"
            )
        }

        val latest = items.maxByOrNull { it.sourceCreatedAt ?: LocalDateTime.MIN }
        if (latest != null) {
            collectorStateRepository.updateState(
                source = collector.source,
                lastFetchedAt = latest.sourceCreatedAt ?: LocalDateTime.now(),
                lastFetchedId = latest.externalId
            )
        }

        val importantCount = scored.count { it.score >= 0.4 }
        logger.info("${collector.source}: ${items.size}건 수집, ${importantCount}건 중요")
    }

    private fun sendSlackNotification(message: String) {
        if (slackWebhookUrl.isBlank()) return
        try {
            webClient.post()
                .uri(slackWebhookUrl)
                .bodyValue(SlackMessage(
                    text = message,
                    attachments = emptyList()
                ))
                .retrieve()
                .bodyToMono(String::class.java)
                .subscribe()
        } catch (e: Exception) {
            logger.debug("Slack 알림 발송 실패: {}", e.message)
        }
    }
}
