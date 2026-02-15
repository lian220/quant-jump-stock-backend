package com.quantjumpstock.core.domain.news.port.output

import com.quantjumpstock.core.domain.news.model.NewsItem
import com.quantjumpstock.core.domain.news.model.NewsSource

interface NewsRepository {
    fun saveAll(items: List<NewsItem>): List<NewsItem>
    fun findRecent(limit: Int): List<NewsItem>
    fun findByTickers(tickers: List<String>, limit: Int): List<NewsItem>
    fun findByTags(tags: List<String>, limit: Int): List<NewsItem>
    fun existsBySourceAndExternalId(source: NewsSource, externalId: String): Boolean
}
