package com.quantjumpstock.core.domain.news.model

import java.time.LocalDateTime

data class NewsItem(
    val id: String? = null,
    val externalId: String,
    val source: NewsSource,
    val originalSource: String?,
    val titleKo: String,
    val titleEn: String?,
    val contentKo: String?,
    val summaryKo: String?,
    val tags: List<String>,
    val tickers: List<String>,
    val importanceScore: Double,
    val isHeadlineOnly: Boolean,
    val isHidden: Boolean = false,
    val viewCount: Int,
    val sourceUrl: String?,
    val sourceCreatedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val extra: Map<String, Any?>
)
