package com.quantjumpstock.core.domain.model.strategy

enum class SlippageModel {
    NONE,
    FIXED,
    ADAPTIVE;

    companion object {
        fun fromString(value: String): SlippageModel {
            return when (value.lowercase()) {
                "none" -> NONE
                "fixed" -> FIXED
                "adaptive" -> ADAPTIVE
                else -> throw IllegalArgumentException("Unknown slippage model: $value")
            }
        }
    }
}

enum class TaxAppliesTo {
    BUY,
    SELL,
    BOTH;

    companion object {
        fun fromString(value: String): TaxAppliesTo {
            return when (value.lowercase()) {
                "buy" -> BUY
                "sell" -> SELL
                "both" -> BOTH
                else -> throw IllegalArgumentException("Unknown tax applies_to: $value")
            }
        }
    }
}

data class CommissionConfig(
    val rate: Double = 0.015,
    val minFee: Double = 0.0
) {
    init {
        require(rate in 0.0..1.0) {
            "commission.rate must be between 0 and 1.0, got $rate"
        }
        require(minFee >= 0.0) {
            "commission.min_fee must be 0 or greater, got $minFee"
        }
    }
}

data class TaxConfig(
    val rate: Double = 0.23,
    val appliesTo: TaxAppliesTo = TaxAppliesTo.SELL
) {
    init {
        require(rate in 0.0..1.0) {
            "tax.rate must be between 0 and 1.0, got $rate"
        }
    }
}

data class SlippageConfig(
    val model: SlippageModel = SlippageModel.ADAPTIVE,
    val fixedBps: Int = 5,
    val volumeImpact: Double = 0.1
) {
    init {
        require(fixedBps in 0..100) {
            "slippage.fixed_bps must be between 0 and 100, got $fixedBps"
        }
        require(volumeImpact in 0.0..1.0) {
            "slippage.volume_impact must be between 0 and 1.0, got $volumeImpact"
        }
    }
}

data class TradingCosts(
    val commission: CommissionConfig = CommissionConfig(),
    val tax: TaxConfig = TaxConfig(),
    val slippage: SlippageConfig = SlippageConfig()
) {
    companion object {
        val DEFAULT = TradingCosts()
    }
}
