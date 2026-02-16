package com.quantjumpstock.core.domain.news.model

data class ScoredNewsItem(
    val newsItem: NewsItem,
    val score: Double,
    val matchedTickers: Set<String>,
    val reasons: List<String>,
    val isRumor: Boolean = false
)
