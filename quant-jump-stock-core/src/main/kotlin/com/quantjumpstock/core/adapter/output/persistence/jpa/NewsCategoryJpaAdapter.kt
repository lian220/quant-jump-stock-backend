package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.quantjumpstock.core.domain.news.model.NewsCategory
import com.quantjumpstock.core.domain.news.model.TagMapping
import com.quantjumpstock.core.domain.news.port.output.NewsCategoryRepository
import com.quantjumpstock.core.domain.news.port.output.TagMappingRepository
import org.springframework.stereotype.Component

@Component
class NewsCategoryJpaAdapter(
    private val categoryRepository: NewsCategoryJpaRepository,
    private val tagMappingRepository: NewsSourceTagMappingJpaRepository
) : NewsCategoryRepository, TagMappingRepository {

    // === NewsCategoryRepository ===

    override fun findActiveCategories(): List<NewsCategory> {
        return categoryRepository.findByIsActiveTrue().map { it.toDomain() }
    }

    override fun findActiveCategoriesSorted(): List<NewsCategory> {
        return categoryRepository.findByIsActiveTrueOrderBySortOrder().map { it.toDomain() }
    }

    override fun findByName(name: String): NewsCategory? {
        return categoryRepository.findByName(name)?.toDomain()
    }

    override fun findAllSorted(): List<NewsCategory> {
        return categoryRepository.findAll()
            .sortedWith(compareBy({ it.categoryGroup }, { it.sortOrder }))
            .map { it.toDomain() }
    }

    override fun findById(id: Long): NewsCategory? {
        return categoryRepository.findById(id).orElse(null)?.toDomain()
    }

    override fun save(category: NewsCategory): NewsCategory {
        val entity = category.toEntity()
        return categoryRepository.save(entity).toDomain()
    }

    override fun delete(id: Long) {
        categoryRepository.deleteById(id)
    }

    // === TagMappingRepository ===

    override fun findAll(): List<TagMapping> {
        return tagMappingRepository.findAll().map {
            TagMapping(
                source = it.source,
                sourceTag = it.sourceTag,
                categoryName = it.category.name
            )
        }
    }

    // === Mapping ===

    private fun NewsCategoryEntity.toDomain() = NewsCategory(
        id = id,
        name = name,
        nameEn = nameEn,
        categoryGroup = categoryGroup,
        description = description,
        icon = icon,
        weight = weight,
        isActive = isActive,
        sortOrder = sortOrder,
        createdAt = createdAt
    )

    private fun NewsCategory.toEntity() = NewsCategoryEntity(
        id = id,
        name = name,
        nameEn = nameEn,
        categoryGroup = categoryGroup,
        description = description,
        icon = icon,
        weight = weight,
        isActive = isActive,
        sortOrder = sortOrder
    )
}
