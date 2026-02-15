package com.quantjumpstock.core.adapter.output.persistence.mongodb

import com.quantjumpstock.core.domain.news.model.CollectorState
import com.quantjumpstock.core.domain.news.model.NewsSource
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Document(collection = "news_collector_state")
data class CollectorStateDocument(
    @Id val id: String? = null,
    @Indexed(unique = true) val source: String,
    @Field("last_fetched_at") val lastFetchedAt: LocalDateTime?,
    @Field("last_fetched_id") val lastFetchedId: String?,
    @Field("fetch_count") val fetchCount: Long = 0,
    @Field("consecutive_errors") val consecutiveErrors: Int = 0,
    @Field("total_errors") val totalErrors: Long = 0,
    @Field("avg_response_time_ms") val avgResponseTimeMs: Long = 0,
    @Field("last_error") val lastError: String? = null,
    @Field("last_error_at") val lastErrorAt: LocalDateTime? = null,
    @Field("updated_at") val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun toDomain(): CollectorState = CollectorState(
        source = NewsSource.valueOf(source),
        lastFetchedAt = lastFetchedAt,
        lastFetchedId = lastFetchedId,
        fetchCount = fetchCount,
        consecutiveErrors = consecutiveErrors,
        totalErrors = totalErrors,
        avgResponseTimeMs = avgResponseTimeMs,
        lastError = lastError,
        lastErrorAt = lastErrorAt,
        updatedAt = updatedAt
    )
}

@Repository
interface CollectorStateMongoRepository : MongoRepository<CollectorStateDocument, String> {
    fun findBySource(source: String): CollectorStateDocument?
}
