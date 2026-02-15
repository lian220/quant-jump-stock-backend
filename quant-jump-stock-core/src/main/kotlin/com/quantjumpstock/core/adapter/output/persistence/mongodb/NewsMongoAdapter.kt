package com.quantjumpstock.core.adapter.output.persistence.mongodb

import com.quantjumpstock.core.domain.news.model.NewsItem
import com.quantjumpstock.core.domain.news.model.NewsSource
import com.quantjumpstock.core.domain.news.port.output.CollectorStateRepository
import com.quantjumpstock.core.domain.news.port.output.NewsRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class NewsMongoAdapter(
    private val mongoTemplate: MongoTemplate
) : NewsRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun saveAll(items: List<NewsItem>): List<NewsItem> {
        return items.mapNotNull { item ->
            try {
                val doc = NewsDocument.fromDomain(item)
                val query = Query(
                    Criteria.where("source").`is`(doc.source)
                        .and("external_id").`is`(doc.externalId)
                )
                val update = Update()
                    .set("original_source", doc.originalSource)
                    .set("title_ko", doc.titleKo)
                    .set("title_en", doc.titleEn)
                    .set("content_ko", doc.contentKo)
                    .set("summary_ko", doc.summaryKo)
                    .set("tags", doc.tags)
                    .set("tickers", doc.tickers)
                    .set("importance_score", doc.importanceScore)
                    .set("is_headline_only", doc.isHeadlineOnly)
                    .set("view_count", doc.viewCount)
                    .set("source_url", doc.sourceUrl)
                    .set("source_created_at", doc.sourceCreatedAt)
                    .set("created_at", doc.createdAt)
                    .set("extra", doc.extra)

                val result = mongoTemplate.upsert(query, update, NewsDocument::class.java)
                if (result.upsertedId != null) {
                    item.copy(id = result.upsertedId.toString())
                } else {
                    item
                }
            } catch (e: Exception) {
                logger.warn("뉴스 저장 실패: source=${item.source}, externalId=${item.externalId}", e)
                null
            }
        }
    }

    override fun findRecent(limit: Int): List<NewsItem> {
        val query = Query()
            .with(Sort.by(Sort.Direction.DESC, "created_at"))
            .limit(limit)
        return mongoTemplate.find(query, NewsDocument::class.java).map { it.toDomain() }
    }

    override fun findByTickers(tickers: List<String>, limit: Int): List<NewsItem> {
        val query = Query(Criteria.where("tickers").`in`(tickers))
            .with(Sort.by(Sort.Direction.DESC, "created_at"))
            .limit(limit)
        return mongoTemplate.find(query, NewsDocument::class.java).map { it.toDomain() }
    }

    override fun findByTags(tags: List<String>, limit: Int): List<NewsItem> {
        val query = Query(Criteria.where("tags").`in`(tags))
            .with(Sort.by(Sort.Direction.DESC, "created_at"))
            .limit(limit)
        return mongoTemplate.find(query, NewsDocument::class.java).map { it.toDomain() }
    }

    override fun existsBySourceAndExternalId(source: NewsSource, externalId: String): Boolean {
        val query = Query(
            Criteria.where("source").`is`(source.name)
                .and("external_id").`is`(externalId)
        )
        return mongoTemplate.exists(query, NewsDocument::class.java)
    }
}

@Component
class CollectorStateMongoAdapter(
    private val repository: CollectorStateMongoRepository,
    private val mongoTemplate: MongoTemplate
) : CollectorStateRepository {

    override fun getState(source: NewsSource): com.quantjumpstock.core.domain.news.model.CollectorState? {
        return repository.findBySource(source.name)?.toDomain()
    }

    override fun getLastFetchedAt(source: NewsSource): LocalDateTime? {
        return repository.findBySource(source.name)?.lastFetchedAt
    }

    override fun getLastFetchedId(source: NewsSource): String? {
        return repository.findBySource(source.name)?.lastFetchedId
    }

    override fun updateState(source: NewsSource, lastFetchedAt: LocalDateTime, lastFetchedId: String?) {
        val query = Query(Criteria.where("source").`is`(source.name))
        val update = Update()
            .set("last_fetched_at", lastFetchedAt)
            .set("last_fetched_id", lastFetchedId)
            .inc("fetch_count", 1)
            .set("consecutive_errors", 0)
            .set("updated_at", LocalDateTime.now())
        mongoTemplate.upsert(query, update, CollectorStateDocument::class.java)
    }

    override fun recordError(source: NewsSource, error: String) {
        val query = Query(Criteria.where("source").`is`(source.name))
        val update = Update()
            .inc("consecutive_errors", 1)
            .inc("total_errors", 1)
            .set("last_error", error.take(500))
            .set("last_error_at", LocalDateTime.now())
            .set("updated_at", LocalDateTime.now())
        mongoTemplate.upsert(query, update, CollectorStateDocument::class.java)
    }

    override fun recordSuccess(source: NewsSource, responseTimeMs: Long) {
        val query = Query(Criteria.where("source").`is`(source.name))
        val update = Update()
            .set("consecutive_errors", 0)
            .set("avg_response_time_ms", responseTimeMs)
            .set("updated_at", LocalDateTime.now())
        mongoTemplate.upsert(query, update, CollectorStateDocument::class.java)
    }
}
