package com.quantjumpstock.core.application.news

import com.quantjumpstock.core.domain.news.model.NewsItem
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "뉴스 응답 DTO")
data class NewsDto(
    @Schema(description = "뉴스 ID")
    val id: String?,

    @Schema(description = "제목 (한국어)")
    val title: String,

    @Schema(description = "제목 (영어)")
    val titleEn: String?,

    @Schema(description = "요약 (한국어)")
    val summary: String?,

    @Schema(description = "본문 (한국어)")
    val content: String?,

    @Schema(description = "뉴스 소스", example = "SAVETICKER")
    val source: String,

    @Schema(description = "원본 소스명", example = "Reuters")
    val originalSource: String?,

    @Schema(description = "태그 목록")
    val tags: List<String>,

    @Schema(description = "관련 티커 목록")
    val tickers: List<String>,

    @Schema(description = "중요도 점수 (0.0~1.0)", example = "0.75")
    val importanceScore: Double,

    @Schema(description = "원본 URL")
    val sourceUrl: String?,

    @Schema(description = "생성일시")
    val createdAt: LocalDateTime?
) {
    companion object {
        fun from(item: NewsItem): NewsDto = NewsDto(
            id = item.id,
            title = item.titleKo,
            titleEn = item.titleEn,
            summary = item.summaryKo,
            content = item.contentKo,
            source = item.source.name,
            originalSource = item.originalSource,
            tags = item.tags,
            tickers = item.tickers,
            importanceScore = item.importanceScore,
            sourceUrl = item.sourceUrl,
            createdAt = item.createdAt
        )
    }
}

@Schema(description = "뉴스 목록 응답")
data class NewsListResponse(
    @Schema(description = "뉴스 목록")
    val news: List<NewsDto>,

    @Schema(description = "총 건수")
    val total: Int
)
