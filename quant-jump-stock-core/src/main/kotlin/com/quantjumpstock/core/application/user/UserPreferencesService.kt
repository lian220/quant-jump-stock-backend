package com.quantjumpstock.core.application.user

import com.quantjumpstock.core.domain.model.user.UserPreferences
import com.quantjumpstock.core.domain.port.output.UserPreferencesRepository
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자 투자 성향 서비스
 */
@Service
class UserPreferencesService(
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 투자 성향 조회
     */
    @Transactional(readOnly = true)
    fun getPreferences(userPk: Long): PreferencesResponse? {
        return userPreferencesRepository.findByUserId(userPk)?.let { toResponse(it) }
    }

    /**
     * 투자 성향 저장 (신규/업데이트 공통)
     */
    @Transactional
    fun savePreferences(userPk: Long, request: SavePreferencesRequest): PreferencesResponse {
        logger.info("💾 투자 성향 저장: userId=$userPk")

        val preferences = UserPreferences(
            userId = userPk,
            investmentCategories = request.investmentCategories ?: emptyList(),
            markets = request.markets ?: emptyList(),
            riskTolerance = request.riskTolerance,
            onboardingCompleted = true
        )

        val saved = userPreferencesRepository.save(preferences)
        logger.info("✅ 투자 성향 저장 완료: userId=$userPk")
        return toResponse(saved)
    }

    private fun toResponse(prefs: UserPreferences) = PreferencesResponse(
        investmentCategories = prefs.investmentCategories,
        markets = prefs.markets,
        riskTolerance = prefs.riskTolerance,
        onboardingCompleted = prefs.onboardingCompleted
    )
}

/**
 * 투자 성향 저장 요청
 */
data class SavePreferencesRequest(
    @field:Size(max = 20, message = "투자 카테고리는 최대 20개까지 입력할 수 있습니다")
    val investmentCategories: List<String>? = null,

    @field:Size(max = 20, message = "시장은 최대 20개까지 입력할 수 있습니다")
    val markets: List<String>? = null,

    @field:Size(max = 20, message = "위험 허용 범위 값이 너무 깁니다")
    @field:Pattern(regexp = "^(CONSERVATIVE|MODERATE|AGGRESSIVE)?$", message = "위험 허용 범위는 CONSERVATIVE, MODERATE, AGGRESSIVE 중 하나여야 합니다")
    val riskTolerance: String? = null
)

/**
 * 투자 성향 응답
 */
data class PreferencesResponse(
    val investmentCategories: List<String>,
    val markets: List<String>,
    val riskTolerance: String?,
    val onboardingCompleted: Boolean
)
