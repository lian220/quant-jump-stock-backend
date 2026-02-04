package com.quantjumpstock.core.domain.model.strategy

import java.time.LocalDateTime

/**
 * StrategyCategory 도메인 모델 - 순수 Kotlin
 */
data class StrategyCategory(
    val id: Long? = null,
    val code: String,
    val name: String,
    val description: String? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(code.isNotBlank()) { "Category code must not be blank" }
        require(code.length <= 50) { "Category code must be at most 50 characters" }
        require(name.isNotBlank()) { "Category name must not be blank" }
        require(name.length <= 100) { "Category name must be at most 100 characters" }
    }

    /**
     * 카테고리 활성화
     */
    fun activate(): StrategyCategory = copy(isActive = true, updatedAt = LocalDateTime.now())

    /**
     * 카테고리 비활성화
     */
    fun deactivate(): StrategyCategory = copy(isActive = false, updatedAt = LocalDateTime.now())
}
