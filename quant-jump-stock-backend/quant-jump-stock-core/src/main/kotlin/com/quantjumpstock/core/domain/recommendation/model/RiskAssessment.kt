package com.quantjumpstock.core.domain.recommendation.model

import java.math.BigDecimal

/**
 * 리스크 평가 Value Object
 *
 * 종목의 변동성, 최대 낙폭 등을 종합하여 리스크 수준을 판단.
 */
data class RiskAssessment(
    val level: RiskLevel,
    val volatility: BigDecimal,          // 변동성 (표준편차, 0~1)
    val maxDrawdown: BigDecimal?,        // 최대 낙폭 (%, nullable)
    val beta: BigDecimal?,               // 시장 베타 (nullable)
    val positionSizePercent: BigDecimal  // 추천 포지션 크기 (%)
) {
    /**
     * 허용 가능한 리스크인가?
     */
    fun isAcceptable(): Boolean = level != RiskLevel.EXTREME

    /**
     * 고위험인가?
     */
    fun isHigh(): Boolean = level == RiskLevel.HIGH || level == RiskLevel.EXTREME

    companion object {
        /**
         * 변동성 기반 리스크 평가 (간단 버전)
         */
        fun fromVolatility(volatility: BigDecimal): RiskAssessment {
            val level = when {
                volatility > BigDecimal("0.5") -> RiskLevel.EXTREME
                volatility > BigDecimal("0.3") -> RiskLevel.HIGH
                volatility > BigDecimal("0.15") -> RiskLevel.MEDIUM
                else -> RiskLevel.LOW
            }

            // 리스크에 따른 포지션 크기 조정
            val positionSize = when (level) {
                RiskLevel.LOW -> BigDecimal("15.0")      // 15%
                RiskLevel.MEDIUM -> BigDecimal("10.0")   // 10%
                RiskLevel.HIGH -> BigDecimal("5.0")      // 5%
                RiskLevel.EXTREME -> BigDecimal("0.0")   // 0% (투자 금지)
            }

            return RiskAssessment(
                level = level,
                volatility = volatility,
                maxDrawdown = null,
                beta = null,
                positionSizePercent = positionSize
            )
        }

        /**
         * 안전한 기본값 (변동성 데이터 없을 때)
         */
        fun default(): RiskAssessment {
            return RiskAssessment(
                level = RiskLevel.MEDIUM,
                volatility = BigDecimal("0.2"),
                maxDrawdown = null,
                beta = null,
                positionSizePercent = BigDecimal("10.0")
            )
        }
    }
}

/**
 * 리스크 수준
 */
enum class RiskLevel(
    val displayName: String,
    val description: String
) {
    LOW(
        displayName = "저위험",
        description = "안정적인 종목, 변동성 낮음"
    ),
    MEDIUM(
        displayName = "중위험",
        description = "일반적인 종목, 적정 변동성"
    ),
    HIGH(
        displayName = "고위험",
        description = "공격적인 종목, 변동성 높음"
    ),
    EXTREME(
        displayName = "극한위험",
        description = "투기적 종목, 투자 부적합"
    )
}
