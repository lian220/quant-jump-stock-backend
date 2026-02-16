package com.quantjumpstock.core.application.news

import com.quantjumpstock.core.domain.economic.port.output.MessagePublisher
import com.quantjumpstock.core.domain.news.model.NewsSource
import com.quantjumpstock.core.domain.news.port.input.NewsCollectionUseCase
import com.quantjumpstock.core.domain.news.port.output.NewsAlertSender
import com.quantjumpstock.core.domain.news.port.output.NewsRepository
import com.quantjumpstock.core.events.EventTopics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 뉴스 처리 서비스 (스코어링 + 알림)
 *
 * 수집 로직은 Data Engine으로 이관됨.
 * Core에서는 Pub/Sub을 통해 수집 요청을 보내고,
 * Data Engine이 수집한 뉴스를 받아 스코어링/알림만 처리.
 */
@Service
class NewsCollectionService(
    private val messagePublisher: MessagePublisher,
    private val newsRepository: NewsRepository,
    private val scorer: NewsScorer,
    private val subscriptionService: NewsSubscriptionService,
    private val newsAlertSender: NewsAlertSender
) : NewsCollectionUseCase {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun requestCollection() {
        val requestId = UUID.randomUUID().toString()
        logger.info("뉴스 수집 요청 발행: requestId=$requestId")
        messagePublisher.publishNewsCollectionRequest(
            topic = EventTopics.NEWS_COLLECTION_REQUEST,
            requestId = requestId,
            source = NewsSource.SAVETICKER.name
        )
    }

    override fun processCollectedNews(articleIds: List<String>, source: String) {
        if (articleIds.isEmpty()) {
            logger.debug("처리할 뉴스 없음 (source=$source)")
            return
        }

        logger.info("수집된 뉴스 처리 시작: ${articleIds.size}건 (source=$source)")

        // MongoDB에서 수집된 기사 조회
        val articles = articleIds.mapNotNull { id ->
            try {
                newsRepository.findById(id)
                    ?: newsRepository.findBySourceAndExternalId(source, id)
            } catch (e: Exception) {
                logger.warn("기사 조회 실패: id=$id, error=${e.message}")
                null
            }
        }

        if (articles.isEmpty()) {
            logger.warn("MongoDB에서 기사를 찾을 수 없음: articleIds=$articleIds")
            return
        }

        // 스코어링
        val scored = articles.map { scorer.score(it) }

        // 스코어 업데이트 (MongoDB)
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
                logger.warn("알림 생성 실패: newsId=${scoredItem.newsItem.externalId}, error=${e.message}")
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

        val importantCount = scored.count { it.score >= IMPORTANCE_THRESHOLD }
        logger.info("뉴스 처리 완료: ${articles.size}건 스코어링, ${importantCount}건 중요")
    }

    companion object {
        private const val IMPORTANCE_THRESHOLD = 0.4
        private const val HIGH_IMPORTANCE_THRESHOLD = 0.7
    }
}
