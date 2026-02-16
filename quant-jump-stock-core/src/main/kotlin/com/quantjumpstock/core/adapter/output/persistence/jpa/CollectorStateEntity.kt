package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.quantjumpstock.core.domain.news.model.CollectorState
import com.quantjumpstock.core.domain.news.model.NewsSource
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "news_collector_state")
class CollectorStateEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 50)
    val source: String,

    @Column(name = "last_fetched_at")
    var lastFetchedAt: LocalDateTime? = null,

    @Column(name = "last_fetched_id")
    var lastFetchedId: String? = null,

    @Column(name = "fetch_count", nullable = false)
    var fetchCount: Long = 0,

    @Column(name = "consecutive_errors", nullable = false)
    var consecutiveErrors: Int = 0,

    @Column(name = "total_errors", nullable = false)
    var totalErrors: Long = 0,

    @Column(name = "avg_response_time_ms", nullable = false)
    var lastResponseTimeMs: Long = 0,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(name = "last_error_at")
    var lastErrorAt: LocalDateTime? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun toDomain(): CollectorState = CollectorState(
        source = NewsSource.valueOf(source),
        lastFetchedAt = lastFetchedAt,
        lastFetchedId = lastFetchedId,
        fetchCount = fetchCount,
        consecutiveErrors = consecutiveErrors,
        totalErrors = totalErrors,
        lastResponseTimeMs = lastResponseTimeMs,
        lastError = lastError,
        lastErrorAt = lastErrorAt,
        updatedAt = updatedAt
    )
}
