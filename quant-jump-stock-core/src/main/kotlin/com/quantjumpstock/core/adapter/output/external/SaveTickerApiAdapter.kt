package com.quantjumpstock.core.adapter.output.external

import com.quantjumpstock.core.domain.news.model.NewsItem
import com.quantjumpstock.core.domain.news.model.NewsSource
import com.quantjumpstock.core.domain.news.port.output.CollectorStateRepository
import com.quantjumpstock.core.domain.news.port.output.NewsCollector
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.min
import kotlin.math.pow

@Component
class SaveTickerApiAdapter(
    private val webClient: WebClient,
    private val collectorStateRepository: CollectorStateRepository
) : NewsCollector {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val kst = ZoneId.of("Asia/Seoul")

    override val source = NewsSource.SAVETICKER

    @Volatile
    private var consecutiveErrors = 0

    @Volatile
    private var lastPollTime = 0L

    override fun collect(): List<NewsItem> {
        if (!shouldPoll()) {
            return emptyList()
        }

        if (consecutiveErrors > 0) {
            val backoffMinutes = min(2.0.pow(consecutiveErrors).toLong(), 60)
            val backoffMs = backoffMinutes * 60_000
            if (System.currentTimeMillis() - lastPollTime < backoffMs) {
                logger.debug("백오프 중 ({}회 연속 에러, {}분 대기)", consecutiveErrors, backoffMinutes)
                return emptyList()
            }
        }

        val startTime = System.currentTimeMillis()
        lastPollTime = startTime

        return try {
            val lastFetchedId = collectorStateRepository.getLastFetchedId(source)
            val lastFetchedAt = collectorStateRepository.getLastFetchedAt(source)
                ?: LocalDateTime.now().minusHours(1)

            val response = webClient.get()
                .uri("https://api.saveticker.com/api/news/list") { builder ->
                    builder
                        .queryParam("page", 1)
                        .queryParam("page_size", 10)
                        .queryParam("sort", "created_at_desc")
                        .queryParam("label_group", 1)
                        .queryParam("label_name", 1)
                        .build()
                }
                .header("User-Agent", "AlphaFoundry/1.0 (News Aggregator)")
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(SaveTickerResponse::class.java)
                .block() ?: return emptyList()

            val elapsed = System.currentTimeMillis() - startTime
            consecutiveErrors = 0
            collectorStateRepository.recordSuccess(source, elapsed)

            val newItems = response.newsList
                .filter { item ->
                    if (lastFetchedId != null && item.id == lastFetchedId) return@filter false
                    val itemCreatedAt = parseDateTime(item.createdAt)
                    itemCreatedAt != null && itemCreatedAt.isAfter(lastFetchedAt)
                }
                .map { it.toDomain() }

            if (newItems.isNotEmpty()) {
                logger.info("SaveTicker: {}건 신규 뉴스 수집 (응답시간: {}ms)", newItems.size, elapsed)
            }

            newItems
        } catch (e: Exception) {
            consecutiveErrors++
            val elapsed = System.currentTimeMillis() - startTime
            logger.warn(
                "SaveTicker API 호출 실패 ({}회 연속, 응답시간: {}ms): {}",
                consecutiveErrors, elapsed, e.message
            )
            collectorStateRepository.recordError(source, e.message ?: "Unknown error")
            emptyList()
        }
    }

    private fun shouldPoll(): Boolean {
        val now = System.currentTimeMillis()
        val interval = getPollingInterval()
        return (now - lastPollTime) >= interval
    }

    private fun getPollingInterval(): Long {
        val hour = LocalTime.now(kst).hour
        val base = when {
            hour in 22..23 || hour in 0..5 -> 60_000L
            hour in 6..8 -> 120_000L
            hour in 9..15 -> 120_000L
            else -> 180_000L
        }
        val jitter = ThreadLocalRandom.current().nextLong(-10_000, 10_000)
        return base + jitter
    }
}
