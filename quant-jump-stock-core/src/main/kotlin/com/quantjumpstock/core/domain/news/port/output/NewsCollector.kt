package com.quantjumpstock.core.domain.news.port.output

import com.quantjumpstock.core.domain.news.model.NewsItem
import com.quantjumpstock.core.domain.news.model.NewsSource

interface NewsCollector {
    val source: NewsSource
    fun collect(): List<NewsItem>
}
