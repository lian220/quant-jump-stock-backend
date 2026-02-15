package com.quantjumpstock.core.adapter.output.persistence.mongodb

import com.quantjumpstock.core.domain.news.model.NewsItem
import com.quantjumpstock.core.domain.news.model.NewsSource
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.LocalDateTime

@Document(collection = "news")
@CompoundIndexes(
    CompoundIndex(
        name = "idx_source_external_id",
        def = "{'source': 1, 'external_id': 1}",
        unique = true,
        background = true
    ),
    CompoundIndex(
        name = "idx_importance_created",
        def = "{'importance_score': -1, 'created_at': -1}",
        background = true
    ),
    CompoundIndex(
        name = "idx_tickers_created",
        def = "{'tickers': 1, 'created_at': -1}",
        background = true
    ),
    CompoundIndex(
        name = "idx_tags_created",
        def = "{'tags': 1, 'created_at': -1}",
        background = true
    )
)
data class NewsDocument(
    @Id val id: String? = null,
    @Field("external_id") val externalId: String,
    @Indexed val source: String,
    @Field("original_source") val originalSource: String?,
    @Field("title_ko") val titleKo: String,
    @Field("title_en") val titleEn: String?,
    @Field("content_ko") val contentKo: String?,
    @Field("summary_ko") val summaryKo: String?,
    val tags: List<String>,
    val tickers: List<String>,
    @Field("importance_score") val importanceScore: Double,
    @Field("is_headline_only") val isHeadlineOnly: Boolean,
    @Field("view_count") val viewCount: Int,
    @Field("source_url") val sourceUrl: String?,
    @Field("source_created_at") val sourceCreatedAt: LocalDateTime?,
    @Field("created_at") val createdAt: LocalDateTime?,
    val extra: Map<String, Any?>
) {
    fun toDomain(): NewsItem = NewsItem(
        id = id,
        externalId = externalId,
        source = NewsSource.valueOf(source),
        originalSource = originalSource,
        titleKo = titleKo,
        titleEn = titleEn,
        contentKo = contentKo,
        summaryKo = summaryKo,
        tags = tags,
        tickers = tickers,
        importanceScore = importanceScore,
        isHeadlineOnly = isHeadlineOnly,
        viewCount = viewCount,
        sourceUrl = sourceUrl,
        sourceCreatedAt = sourceCreatedAt,
        createdAt = createdAt,
        extra = extra
    )

    companion object {
        fun fromDomain(item: NewsItem): NewsDocument = NewsDocument(
            id = item.id,
            externalId = item.externalId,
            source = item.source.name,
            originalSource = item.originalSource,
            titleKo = item.titleKo,
            titleEn = item.titleEn,
            contentKo = item.contentKo,
            summaryKo = item.summaryKo,
            tags = item.tags,
            tickers = item.tickers,
            importanceScore = item.importanceScore,
            isHeadlineOnly = item.isHeadlineOnly,
            viewCount = item.viewCount,
            sourceUrl = item.sourceUrl,
            sourceCreatedAt = item.sourceCreatedAt,
            createdAt = item.createdAt,
            extra = item.extra
        )
    }
}
