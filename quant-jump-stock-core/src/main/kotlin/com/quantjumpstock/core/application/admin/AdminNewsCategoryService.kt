package com.quantjumpstock.core.application.admin

import com.quantjumpstock.core.adapter.output.persistence.jpa.NewsCategoryEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.NewsCategoryJpaRepository
import com.quantjumpstock.core.application.news.NewsCategoryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminNewsCategoryService(
    private val categoryRepository: NewsCategoryJpaRepository,
    private val newsCategoryService: NewsCategoryService
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /** 전체 카테고리 목록 (비활성 포함) */
    fun getAllCategories(includeInactive: Boolean = true): AdminCategoryListResponse {
        val categories = if (includeInactive) {
            categoryRepository.findAll().sortedWith(compareBy({ it.categoryGroup }, { it.sortOrder }))
        } else {
            categoryRepository.findByIsActiveTrueOrderBySortOrder()
        }

        return AdminCategoryListResponse(
            categories = categories.map { it.toAdminDto() },
            total = categories.size
        )
    }

    /** 카테고리 생성 */
    @Transactional
    fun createCategory(request: CreateCategoryRequest): AdminCategoryDto {
        val existing = categoryRepository.findByName(request.name)
        require(existing == null) { "이미 존재하는 카테고리명입니다: ${request.name}" }

        val entity = NewsCategoryEntity(
            name = request.name,
            nameEn = request.nameEn,
            categoryGroup = request.group,
            description = request.description,
            icon = request.icon,
            weight = request.weight,
            sortOrder = request.sortOrder
        )
        val saved = categoryRepository.save(entity)
        newsCategoryService.refreshCache()
        logger.info("뉴스 카테고리 생성: {} ({})", saved.name, saved.id)
        return saved.toAdminDto()
    }

    /** 카테고리 수정 */
    @Transactional
    fun updateCategory(id: Long, request: UpdateCategoryRequest): AdminCategoryDto {
        val existing = categoryRepository.findById(id)
            .orElseThrow { IllegalArgumentException("카테고리를 찾을 수 없습니다: $id") }

        val updated = NewsCategoryEntity(
            id = existing.id,
            name = request.name ?: existing.name,
            nameEn = request.nameEn ?: existing.nameEn,
            categoryGroup = request.group ?: existing.categoryGroup,
            description = request.description ?: existing.description,
            icon = request.icon ?: existing.icon,
            weight = request.weight ?: existing.weight,
            isActive = request.isActive ?: existing.isActive,
            sortOrder = request.sortOrder ?: existing.sortOrder
        )
        val saved = categoryRepository.save(updated)
        newsCategoryService.refreshCache()
        logger.info("뉴스 카테고리 수정: {} ({})", saved.name, saved.id)
        return saved.toAdminDto()
    }

    /** 카테고리 활성/비활성 토글 */
    @Transactional
    fun toggleCategory(id: Long): AdminCategoryDto {
        val existing = categoryRepository.findById(id)
            .orElseThrow { IllegalArgumentException("카테고리를 찾을 수 없습니다: $id") }

        val toggled = NewsCategoryEntity(
            id = existing.id,
            name = existing.name,
            nameEn = existing.nameEn,
            categoryGroup = existing.categoryGroup,
            description = existing.description,
            icon = existing.icon,
            weight = existing.weight,
            isActive = !existing.isActive,
            sortOrder = existing.sortOrder
        )
        val saved = categoryRepository.save(toggled)
        newsCategoryService.refreshCache()
        logger.info("뉴스 카테고리 토글: {} → isActive={}", saved.name, saved.isActive)
        return saved.toAdminDto()
    }

    /** 카테고리 삭제 */
    @Transactional
    fun deleteCategory(id: Long) {
        val existing = categoryRepository.findById(id)
            .orElseThrow { IllegalArgumentException("카테고리를 찾을 수 없습니다: $id") }
        categoryRepository.delete(existing)
        newsCategoryService.refreshCache()
        logger.info("뉴스 카테고리 삭제: {} ({})", existing.name, id)
    }
}

private fun NewsCategoryEntity.toAdminDto() = AdminCategoryDto(
    id = id!!,
    name = name,
    nameEn = nameEn,
    group = categoryGroup,
    description = description,
    icon = icon,
    weight = weight,
    isActive = isActive,
    sortOrder = sortOrder,
    createdAt = createdAt.toString()
)
