package com.quantjumpstock.core.domain.model.user

/**
 * 사용자 투자 성향 도메인 모델
 */
data class UserPreferences(
    val id: Long? = null,
    val userId: Long,
    val investmentCategories: List<String> = emptyList(),
    val markets: List<String> = emptyList(),
    val riskTolerance: String? = null,
    val onboardingCompleted: Boolean = false
)
