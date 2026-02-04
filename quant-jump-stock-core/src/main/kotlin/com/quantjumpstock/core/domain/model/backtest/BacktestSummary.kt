package com.quantjumpstock.core.domain.model.backtest

import java.math.BigDecimal
import java.time.LocalDate

/**
 * 백테스트 결과 요약 (마켓플레이스 조회용)
 */
data class BacktestSummary(
    val id: Long,
    val cagr: BigDecimal,
    val mdd: BigDecimal,
    val sharpeRatio: BigDecimal?,
    val totalReturn: BigDecimal,
    val volatility: BigDecimal?,
    val winRate: BigDecimal?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: BacktestStatus
)
