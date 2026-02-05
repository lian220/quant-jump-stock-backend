package com.quantjumpstock.core.domain.model

import java.math.BigDecimal

/**
 * 백테스트 요청 Domain Model
 */
data class BacktestRequest(
    val requestId: String,
    val strategyId: Long,
    val startDate: String,      // yyyy-MM-dd
    val endDate: String,        // yyyy-MM-dd
    val initialCapital: BigDecimal,
    val timestamp: String,
    val source: String = "quantiq-core",
    val userId: String? = null
)
