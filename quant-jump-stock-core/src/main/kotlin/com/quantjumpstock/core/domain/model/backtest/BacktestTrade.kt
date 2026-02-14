package com.quantjumpstock.core.domain.model.backtest

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 백테스트 거래 내역 도메인 모델
 *
 * JPA Entity가 아닌 순수 도메인 객체입니다.
 * 영속성 관련 어노테이션 없이 비즈니스 로직만 포함합니다.
 */
data class BacktestTrade(
    val id: Long? = null,
    val backtestResultId: Long? = null,
    val tradeDate: LocalDate,
    val ticker: String,
    val side: BacktestTradeSide,
    val quantity: Int,
    val price: BigDecimal,
    val amount: BigDecimal,
    val commission: BigDecimal = BigDecimal.ZERO,

    // P&L (for SELL trades)
    val pnl: BigDecimal? = null,
    val pnlPercent: BigDecimal? = null,
    val holdingDays: Int? = null,
    val signalReason: String? = null,

    // SCRUM-330: Exit Reason & Cost Details
    val exitReason: String? = null,
    val slippageAmount: BigDecimal? = null,
    val taxAmount: BigDecimal? = null,
    val executionPrice: BigDecimal? = null,

    // Timestamps
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 백테스트 거래 방향
 */
enum class BacktestTradeSide {
    BUY,
    SELL
}
