package com.quantjumpstock.core.application.category

import com.quantjumpstock.core.domain.model.strategy.StrategyCategory
import com.quantjumpstock.core.domain.port.output.StrategyCategoryRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CategoryService(
    private val categoryRepository: StrategyCategoryRepository
) {

    /**
     * 활성화된 모든 카테고리 조회 (정렬순서대로)
     * 캐싱: 24시간 TTL (카테고리는 거의 변경되지 않음)
     */
    @Cacheable(value = ["categories"], key = "'all'")
    @Transactional(readOnly = true)
    fun getAllCategories(): CategoriesResponse {
        val categories = categoryRepository.findAllActive()
            .map { it.toDto() }

        return CategoriesResponse(
            categories = categories,
            total = categories.size
        )
    }

    /**
     * 코드로 카테고리 조회
     */
    @Transactional(readOnly = true)
    fun getCategoryByCode(code: String): CategoryDto? {
        return categoryRepository.findByCode(code)?.toDto()
    }

    /**
     * ID로 카테고리 조회
     */
    @Transactional(readOnly = true)
    fun getCategoryById(id: Long): CategoryDto? {
        return categoryRepository.findById(id)?.toDto()
    }

    private fun StrategyCategory.toDto() = CategoryDto(
        id = this.id ?: throw IllegalStateException("카테고리에 ID가 없습니다: code=$code"),
        code = this.code,
        name = this.name,
        description = this.description,
        icon = this.icon,
        sortOrder = this.sortOrder
    )
}
