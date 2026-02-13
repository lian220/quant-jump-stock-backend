package com.quantjumpstock.core.domain.recommendation.model

/**
 * 시장 컨텍스트 Value Object
 *
 * 현재 시장 상황(추세, 변동성)을 나타냄.
 * 추천 점수 계산 시 시장 상황을 반영하기 위해 사용.
 */
data class MarketContext(
    val trend: MarketTrend,
    val volatility: MarketVolatility
) {
    /**
     * 강세장인가?
     */
    fun isBullish(): Boolean = trend == MarketTrend.BULL

    /**
     * 약세장인가?
     */
    fun isBearish(): Boolean = trend == MarketTrend.BEAR

    /**
     * 고변동성 시장인가?
     */
    fun isHighVolatility(): Boolean = volatility == MarketVolatility.HIGH

    companion object {
        /**
         * 기본값 (중립 시장, 중간 변동성)
         */
        fun default(): MarketContext {
            return MarketContext(
                trend = MarketTrend.SIDEWAYS,
                volatility = MarketVolatility.MEDIUM
            )
        }

        /**
         * 강세장 (낮은 변동성)
         */
        fun bullMarket(): MarketContext {
            return MarketContext(
                trend = MarketTrend.BULL,
                volatility = MarketVolatility.MEDIUM
            )
        }

        /**
         * 약세장 (높은 변동성)
         */
        fun bearMarket(): MarketContext {
            return MarketContext(
                trend = MarketTrend.BEAR,
                volatility = MarketVolatility.HIGH
            )
        }
    }
}

/**
 * 시장 추세
 */
enum class MarketTrend(
    val displayName: String,
    val description: String
) {
    BULL(
        displayName = "강세장",
        description = "상승 추세, 낙관적 시장"
    ),
    BEAR(
        displayName = "약세장",
        description = "하락 추세, 비관적 시장"
    ),
    SIDEWAYS(
        displayName = "횡보장",
        description = "중립 추세, 박스권 시장"
    )
}

/**
 * 시장 변동성
 */
enum class MarketVolatility(
    val displayName: String,
    val description: String
) {
    LOW(
        displayName = "저변동성",
        description = "안정적인 시장"
    ),
    MEDIUM(
        displayName = "중변동성",
        description = "일반적인 시장"
    ),
    HIGH(
        displayName = "고변동성",
        description = "불안정한 시장"
    )
}
