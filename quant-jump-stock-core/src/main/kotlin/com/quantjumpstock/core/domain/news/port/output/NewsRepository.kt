package com.quantjumpstock.core.domain.news.port.output

import com.quantjumpstock.core.domain.news.model.NewsItem
import com.quantjumpstock.core.domain.news.model.NewsSource
import org.springframework.data.domain.Pageable

interface NewsRepository {
    fun saveAll(items: List<NewsItem>): List<NewsItem>
    fun findRecent(limit: Int): List<NewsItem>
    fun findByTickers(tickers: List<String>, limit: Int): List<NewsItem>
    fun findByTags(tags: List<String>, limit: Int): List<NewsItem>
    fun existsBySourceAndExternalId(source: NewsSource, externalId: String): Boolean
    fun findBySourceNeedingEnrichment(source: NewsSource, maxContentLength: Int = 200): List<NewsItem>

    // Admin 기사 관리용
    fun findAllPaginated(
        source: String?,
        tag: String?,
        ticker: String?,
        dateFrom: java.time.LocalDateTime?,
        dateTo: java.time.LocalDateTime?,
        includeHidden: Boolean,
        pageable: Pageable
    ): Pair<List<NewsItem>, Long>
    fun findById(id: String): NewsItem?
    fun save(item: NewsItem): NewsItem
    fun updateMetadata(id: String, updates: Map<String, Any?>)
    fun softDelete(id: String)
    fun restore(id: String)
    fun countAll(): Long
    fun countHidden(): Long
    fun averageImportance(): Double
}
