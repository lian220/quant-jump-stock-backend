package com.quantjumpstock.core.adapter.output.external

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.quantjumpstock.core.domain.news.model.NewsItem
import com.quantjumpstock.core.domain.news.model.NewsSource
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@JsonIgnoreProperties(ignoreUnknown = true)
data class SaveTickerResponse(
    @JsonProperty("news_list") val newsList: List<SaveTickerNewsItem> = emptyList(),
    @JsonProperty("total_count") val totalCount: Int = 0,
    val page: Int = 1,
    @JsonProperty("from_cache") val fromCache: Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SaveTickerNewsItem(
    val id: String,
    val title: String,
    val content: String? = null,
    val source: String? = null,
    @JsonProperty("created_at") val createdAt: String,
    @JsonProperty("view_count") val viewCount: Int = 0,
    @JsonProperty("tag_names") val tagNames: List<String> = emptyList(),
    @JsonProperty("is_headline_only") val isHeadlineOnly: Boolean = false,
    val translations: SaveTickerTranslations? = null,
    val extra: Map<String, Any?>? = null
) {
    fun toDomain(): NewsItem {
        val koTranslation = translations?.translated?.get("ko_KR")
        val enTranslation = translations?.translated?.get("en_US")

        val (tickers, tags) = tagNames.partition { it.startsWith("$") }

        return NewsItem(
            externalId = id,
            source = NewsSource.SAVETICKER,
            originalSource = source,
            titleKo = title,
            titleEn = enTranslation?.title,
            contentKo = content?.takeIf { it.isNotBlank() },
            summaryKo = koTranslation?.summary?.firstOrNull()?.content?.takeIf { it.isNotBlank() },
            tags = tags,
            tickers = tickers,
            importanceScore = 0.0,
            isHeadlineOnly = isHeadlineOnly,
            viewCount = viewCount,
            sourceUrl = extra?.get("source_url") as? String,
            sourceCreatedAt = (extra?.get("source_created_at") as? String)?.let { parseDateTime(it) },
            createdAt = parseDateTime(createdAt),
            extra = extra ?: emptyMap()
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SaveTickerTranslations(
    @JsonProperty("source_locale") val sourceLocale: String? = null,
    val translated: Map<String, SaveTickerTranslatedContent>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SaveTickerTranslatedContent(
    val title: String? = null,
    val content: List<SaveTickerContentBlock>? = null,
    val summary: List<SaveTickerContentBlock>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SaveTickerContentBlock(
    val type: String? = null,
    val content: String? = null
)

private val OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME
private val ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME

fun parseDateTime(dateStr: String): LocalDateTime? {
    return try {
        OffsetDateTime.parse(dateStr, OFFSET_FORMATTER).toLocalDateTime()
    } catch (e: DateTimeParseException) {
        try {
            LocalDateTime.parse(dateStr, ISO_FORMATTER)
        } catch (e2: DateTimeParseException) {
            try {
                LocalDateTime.parse(dateStr.replace("Z", ""))
            } catch (e3: DateTimeParseException) {
                null
            }
        }
    }
}
