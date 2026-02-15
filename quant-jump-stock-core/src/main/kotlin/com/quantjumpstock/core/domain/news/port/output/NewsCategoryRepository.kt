package com.quantjumpstock.core.domain.news.port.output

import com.quantjumpstock.core.domain.news.model.NewsCategory

interface NewsCategoryRepository {
    fun findActiveCategories(): List<NewsCategory>
    fun findActiveCategoriesSorted(): List<NewsCategory>
    fun findByName(name: String): NewsCategory?
    fun findAllSorted(): List<NewsCategory>
    fun findById(id: Long): NewsCategory?
    fun save(category: NewsCategory): NewsCategory
    fun delete(id: Long)
}
