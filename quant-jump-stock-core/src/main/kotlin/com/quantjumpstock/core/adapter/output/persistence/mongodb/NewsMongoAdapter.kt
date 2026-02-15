package com.quantjumpstock.core.adapter.output.persistence.mongodb

import com.quantjumpstock.core.domain.news.model.NewsItem
import com.quantjumpstock.core.domain.news.model.NewsSource
import com.quantjumpstock.core.domain.news.port.output.NewsRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
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

    override fun findBySourceNeedingEnrichment(source: NewsSource, maxContentLength: Int): List<NewsItem> {
        val baseCriteria = Criteria.where("source").`is`(source.name)
            .and("is_headline_only").`is`(false)
        val contentCriteria = Criteria().orOperator(
            Criteria.where("content_ko").isNull,
            Criteria.where("content_ko").regex("^.{0,$maxContentLength}$")
        )
        val query = Query(Criteria().andOperator(baseCriteria, contentCriteria))
        return mongoTemplate.find(query, NewsDocument::class.java).map { it.toDomain() }
    }

    override fun existsBySourceAndExternalId(source: NewsSource, externalId: String): Boolean {
        val query = Query(
            Criteria.where("source").`is`(source.name)
                .and("external_id").`is`(externalId)
        )
        return mongoTemplate.exists(query, NewsDocument::class.java)
    }

    // === Admin 기사 관리 ===

    override fun findAllPaginated(
        source: String?,
        tag: String?,
        ticker: String?,
        dateFrom: LocalDateTime?,
        dateTo: LocalDateTime?,
        includeHidden: Boolean,
        pageable: Pageable
    ): Pair<List<NewsItem>, Long> {
        val criteria = mutableListOf<Criteria>()

        if (!includeHidden) {
            criteria.add(
                Criteria().orOperator(
                    Criteria.where("is_hidden").`is`(false),
                    Criteria.where("is_hidden").exists(false)
                )
            )
        }
        source?.let { criteria.add(Criteria.where("source").`is`(it)) }
        tag?.let { criteria.add(Criteria.where("tags").`in`(it)) }
        ticker?.let { criteria.add(Criteria.where("tickers").`in`(it)) }
        dateFrom?.let { criteria.add(Criteria.where("created_at").gte(it)) }
        dateTo?.let { criteria.add(Criteria.where("created_at").lte(it)) }

        val query = if (criteria.isNotEmpty()) {
            Query(Criteria().andOperator(*criteria.toTypedArray()))
        } else {
            Query()
        }

        val total = mongoTemplate.count(query, NewsDocument::class.java)

        query.with(pageable)
        if (pageable.sort.isUnsorted) {
            query.with(Sort.by(Sort.Direction.DESC, "created_at"))
        }

        val items = mongoTemplate.find(query, NewsDocument::class.java).map { it.toDomain() }
        return Pair(items, total)
    }

    override fun findById(id: String): NewsItem? {
        return mongoTemplate.findById(id, NewsDocument::class.java)?.toDomain()
    }

    override fun save(item: NewsItem): NewsItem {
        val doc = NewsDocument.fromDomain(item)
        val saved = mongoTemplate.save(doc)
        return saved.toDomain()
    }

    override fun updateMetadata(id: String, updates: Map<String, Any?>) {
        val query = Query(Criteria.where("_id").`is`(id))
        val update = Update()
        updates.forEach { (key, value) ->
            if (value != null) update.set(key, value) else update.unset(key)
        }
        mongoTemplate.updateFirst(query, update, NewsDocument::class.java)
    }

    override fun softDelete(id: String) {
        val query = Query(Criteria.where("_id").`is`(id))
        val update = Update().set("is_hidden", true)
        mongoTemplate.updateFirst(query, update, NewsDocument::class.java)
    }

    override fun restore(id: String) {
        val query = Query(Criteria.where("_id").`is`(id))
        val update = Update().set("is_hidden", false)
        mongoTemplate.updateFirst(query, update, NewsDocument::class.java)
    }

    override fun countAll(): Long {
        return mongoTemplate.count(Query(), NewsDocument::class.java)
    }

    override fun countHidden(): Long {
        val query = Query(Criteria.where("is_hidden").`is`(true))
        return mongoTemplate.count(query, NewsDocument::class.java)
    }

    override fun averageImportance(): Double {
        val aggregation = Aggregation.newAggregation(
            Aggregation.group().avg("importance_score").`as`("avgImportance")
        )
        val result = mongoTemplate.aggregate(aggregation, "news", Map::class.java)
        val mapped = result.mappedResults.firstOrNull()
        return (mapped?.get("avgImportance") as? Number)?.toDouble() ?: 0.0
    }
}
