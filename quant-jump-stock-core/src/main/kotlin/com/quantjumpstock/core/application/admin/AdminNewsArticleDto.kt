package com.quantjumpstock.core.application.admin

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

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
    @field:NotBlank(message = "제목은 필수입니다")
    @field:Size(max = 500, message = "제목은 500자 이하여야 합니다")
    val titleKo: String,

    @field:Size(max = 500, message = "영문 제목은 500자 이하여야 합니다")
    val titleEn: String? = null,

    @field:Size(max = 10000, message = "본문은 10000자 이하여야 합니다")
    val contentKo: String? = null,

    @field:Size(max = 1000, message = "요약은 1000자 이하여야 합니다")
    val summaryKo: String? = null,

    @field:Size(max = 20, message = "태그는 최대 20개까지 가능합니다")
    val tags: List<String> = emptyList(),

    @field:Size(max = 20, message = "티커는 최대 20개까지 가능합니다")
    val tickers: List<String> = emptyList(),

    val importanceScore: Double = 0.5,

    @field:Size(max = 2000, message = "URL은 2000자 이하여야 합니다")
    val sourceUrl: String? = null
)

data class UpdateNewsArticleRequest(
    @field:Size(max = 500, message = "제목은 500자 이하여야 합니다")
    val titleKo: String? = null,

    @field:Size(max = 500, message = "영문 제목은 500자 이하여야 합니다")
    val titleEn: String? = null,

    @field:Size(max = 1000, message = "요약은 1000자 이하여야 합니다")
    val summaryKo: String? = null,

    @field:Size(max = 20, message = "태그는 최대 20개까지 가능합니다")
    val tags: List<String>? = null,

    @field:Size(max = 20, message = "티커는 최대 20개까지 가능합니다")
    val tickers: List<String>? = null,

    val importanceScore: Double? = null
)
