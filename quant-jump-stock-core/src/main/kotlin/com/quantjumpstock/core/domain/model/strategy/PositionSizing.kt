package com.quantjumpstock.core.domain.model.strategy

enum class PositionSizingMethod {
    FIXED_PERCENTAGE,
    VOLATILITY_ADJUSTED,
    KELLY_CRITERION;

    companion object {
        fun fromString(value: String): PositionSizingMethod {
            return when (value.lowercase()) {
                "fixed_percentage" -> FIXED_PERCENTAGE
                "volatility_adjusted" -> VOLATILITY_ADJUSTED
                "kelly_criterion" -> KELLY_CRITERION
                else -> throw IllegalArgumentException("Unknown position sizing method: $value")
            }
        }
    }
}

data class PositionSizing(
    val method: PositionSizingMethod = PositionSizingMethod.FIXED_PERCENTAGE,
    val maxPositionPct: Double = 10.0,
    val maxPositions: Int = 10,
    val volatilityScaling: Boolean = false,
    val lookbackDays: Int = 20,
    val targetVolatility: Double = 0.15,
    val kellyFraction: Double = 0.5
) {
    init {
        require(maxPositionPct in 1.0..100.0) {
            "maxPositionPct must be between 1 and 100, got $maxPositionPct"
        }
        require(maxPositions in 1..100) {
            "maxPositions must be between 1 and 100, got $maxPositions"
        }
        require(targetVolatility in 0.01..1.0) {
            "targetVolatility must be between 0.01 and 1.0, got $targetVolatility"
        }
        require(lookbackDays in 5..252) {
            "lookbackDays must be between 5 and 252, got $lookbackDays"
        }
        require(kellyFraction in 0.1..1.0) {
            "kellyFraction must be between 0.1 and 1.0, got $kellyFraction"
        }
    }

    companion object {
        val DEFAULT = PositionSizing()
    }
}
