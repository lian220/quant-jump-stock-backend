package com.quantjumpstock.core.domain.model.stock

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class StockPriceSnapshot(
    val ticker: String,
    val date: LocalDate,
    val open: BigDecimal?,
    val high: BigDecimal?,
    val low: BigDecimal?,
    val close: BigDecimal,
    val volume: Long?,
    val previousClose: BigDecimal?,
    val marketCap: BigDecimal?,
    val trailingPE: BigDecimal?
) {
    val changeAmount: BigDecimal?
        get() = previousClose?.let { close.subtract(it) }

    val changePercent: BigDecimal?
        get() = previousClose?.let {
            if (it.compareTo(BigDecimal.ZERO) != 0) {
                close.subtract(it)
                    .divide(it, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                    .setScale(2, RoundingMode.HALF_UP)
            } else null
        }
}
