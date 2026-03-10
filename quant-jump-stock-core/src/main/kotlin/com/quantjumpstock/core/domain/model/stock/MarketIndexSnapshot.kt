package com.quantjumpstock.core.domain.model.stock

import java.math.BigDecimal
import java.time.LocalDate

data class MarketIndexSnapshot(
    val ticker: String,
    val name: String?,
    val date: LocalDate,
    val price: BigDecimal,
    val previousClose: BigDecimal?,
    val changePercent: BigDecimal?
)
