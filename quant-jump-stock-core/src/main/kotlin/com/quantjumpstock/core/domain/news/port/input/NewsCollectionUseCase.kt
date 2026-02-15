package com.quantjumpstock.core.domain.news.port.input

import com.quantjumpstock.core.domain.news.model.NewsSource

interface NewsCollectionUseCase {
    fun collectAll()
    fun collectFrom(source: NewsSource)
    fun enrichExistingArticles(): Int
}
