package com.quantjumpstock.core.domain.model.strategy

data class RiskSettings(
    val stopLoss: StopLossConfig = StopLossConfig(),
    val takeProfit: TakeProfitConfig = TakeProfitConfig(),
    val trailingStop: TrailingStopConfig = TrailingStopConfig(),
    val timeStop: TimeStopConfig = TimeStopConfig()
) {
    companion object {
        val DEFAULT = RiskSettings()
    }
}

data class StopLossConfig(
    val enabled: Boolean = false,
    val value: Double = -10.0
) {
    init {
        require(value in -100.0..0.0) { "StopLoss value must be between -100 and 0, got $value" }
    }
}

data class TakeProfitConfig(
    val enabled: Boolean = false,
    val value: Double = 20.0
) {
    init {
        require(value in 0.0..1000.0) { "TakeProfit value must be between 0 and 1000, got $value" }
    }
}

data class TrailingStopConfig(
    val enabled: Boolean = false,
    val activation: Double = 5.0,
    val trail: Double = 3.0
) {
    init {
        require(activation > 0) { "TrailingStop activation must be positive, got $activation" }
        require(trail > 0) { "TrailingStop trail must be positive, got $trail" }
        require(trail < activation) { "TrailingStop trail ($trail) must be less than activation ($activation)" }
    }
}

data class TimeStopConfig(
    val enabled: Boolean = false,
    val maxHoldingDays: Int = 60
) {
    init {
        require(maxHoldingDays in 1..365) { "maxHoldingDays must be between 1 and 365, got $maxHoldingDays" }
    }
}
