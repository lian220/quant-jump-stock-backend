package com.quantjumpstock.core.domain.news.model

import java.time.LocalDateTime

data class CollectorState(
    val source: NewsSource,
    val lastFetchedAt: LocalDateTime?,
    val lastFetchedId: String?,
    val fetchCount: Long,
    val consecutiveErrors: Int,
    val totalErrors: Long,
    val avgResponseTimeMs: Long,
    val lastError: String?,
    val lastErrorAt: LocalDateTime?,
    val updatedAt: LocalDateTime
)
