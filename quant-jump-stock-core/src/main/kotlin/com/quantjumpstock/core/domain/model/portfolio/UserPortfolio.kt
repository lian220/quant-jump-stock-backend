package com.quantjumpstock.core.domain.model.portfolio

import java.time.LocalDateTime

/**
 * 사용자 포트폴리오 도메인 모델
 */
data class UserPortfolio(
    val id: Long? = null,
    val userId: Long,
    val strategyId: Long? = null,
    val name: String,
    val description: String? = null,
    val isDefault: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(name.isNotBlank()) { "Portfolio name must not be blank" }
        require(name.length <= 100) { "Portfolio name must be 100 characters or less" }
    }
}
