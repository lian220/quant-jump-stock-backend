package com.quantjumpstock.core.application.news

import com.quantjumpstock.core.domain.news.model.NewsSource
import com.quantjumpstock.core.domain.news.port.input.NewsCollectionUseCase
import com.quantjumpstock.core.domain.news.port.output.CollectorStateRepository
import com.quantjumpstock.core.domain.news.port.output.NewsAlertSender
import com.quantjumpstock.core.domain.news.port.output.NewsCollector
import com.quantjumpstock.core.domain.news.port.output.NewsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class NewsCollectionService(
    private val collectors: List<NewsCollector>,
    private val newsRepository: NewsRepository,
    private val collectorStateRepository: CollectorStateRepository,
    private val scorer: NewsScorer,
    private val subscriptionService: NewsSubscriptionService,
    private val newsAlertSender: NewsAlertSender
) : NewsCollectionUseCase {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun collectAll() {
        collectors.forEach { collector ->
            try {
                processCollector(collector)
            } catch (e: Exception) {
                logger.error("${collector.source} 수집 실패", e)
                collectorStateRepository.recordError(collector.source, e.message ?: "Unknown")
            }
        }
    }

    override fun collectFrom(source: NewsSource) {
        val collector = collectors.find { it.source == source }
            ?: throw IllegalArgumentException("Collector not found for source: $source")
        processCollector(collector)
    }

    @Transactional
    open fun processCollector(collector: NewsCollector) {
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
                logger.warn("알림 생성 실패: newsId={}, error={}", scoredItem.newsItem.externalId, e.message)
            }
        }

        // Slack 알림 (중요 뉴스만)
        scored.filter { it.score >= IMPORTANCE_THRESHOLD }.forEach { scoredItem ->
            val emoji = if (scoredItem.score >= HIGH_IMPORTANCE_THRESHOLD) "🚨" else "📰"
            val rumorLabel = if (scoredItem.isRumor) " [미확인]" else ""
            newsAlertSender.sendAlert(
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

        val importantCount = scored.count { it.score >= IMPORTANCE_THRESHOLD }
        logger.info("${collector.source}: ${items.size}건 수집, ${importantCount}건 중요")
    }

    override fun enrichExistingArticles(): Int {
        var enrichedCount = 0
        collectors.forEach { collector ->
            val articles = newsRepository.findBySourceNeedingEnrichment(collector.source)
            if (articles.isEmpty()) return@forEach

            var collectorEnrichedCount = 0
            logger.info("${collector.source}: ${articles.size}건 기존 기사 본문 보강 시작")
            articles.forEach { article ->
                try {
                    val fullContent = collector.enrichContent(article.externalId)
                    if (fullContent != null) {
                        newsRepository.saveAll(listOf(article.copy(contentKo = fullContent)))
                        collectorEnrichedCount++
                        enrichedCount++
                    }
                } catch (e: Exception) {
                    logger.warn("기사 본문 보강 실패: externalId={}, error={}", article.externalId, e.message)
                }
            }
            logger.info("${collector.source}: ${collectorEnrichedCount}건 기사 본문 보강 완료")
        }
        return enrichedCount
    }

    companion object {
        private const val IMPORTANCE_THRESHOLD = 0.4
        private const val HIGH_IMPORTANCE_THRESHOLD = 0.7
    }
}
