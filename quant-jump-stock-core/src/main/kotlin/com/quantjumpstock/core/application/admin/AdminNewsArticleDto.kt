package com.quantjumpstock.core.application.admin

import java.time.LocalDateTime

data class AdminNewsArticle(
    val id: String,
    val externalId: String,
    val source: String,
    val originalSource: String?,
    val titleKo: String,
    val titleEn: String?,
    val contentKo: String?,
    val summaryKo: String?,
    val tags: List<String>,
    val tickers: List<String>,
    val importanceScore: Double,
    val isHeadlineOnly: Boolean,
    val viewCount: Int,
    val sourceUrl: String?,
    val isHidden: Boolean,
    val sourceCreatedAt: String?,
    val createdAt: String?
)

data class AdminNewsArticleListResponse(
    val articles: List<AdminNewsArticle>,
    val total: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int
)

data class AdminNewsArticleStatsResponse(
    val total: Long,
    val active: Long,
    val hidden: Long,
    val avgImportance: Double
)

data class CreateNewsArticleRequest(
    val titleKo: String,
    val titleEn: String? = null,
    val contentKo: String? = null,
    val summaryKo: String? = null,
    val tags: List<String> = emptyList(),
    val tickers: List<String> = emptyList(),
    val importanceScore: Double = 0.5,
    val sourceUrl: String? = null
)

data class UpdateNewsArticleRequest(
    val titleKo: String? = null,
    val titleEn: String? = null,
    val summaryKo: String? = null,
    val tags: List<String>? = null,
    val tickers: List<String>? = null,
    val importanceScore: Double? = null
)
