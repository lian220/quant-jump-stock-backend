package com.quantjumpstock.core.domain.news.port.input

interface NewsCollectionUseCase {
    fun requestCollection()
    fun processCollectedNews(articleIds: List<String>, source: String)
}
