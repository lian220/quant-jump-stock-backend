package com.quantjumpstock.core.domain.model.trading

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * TradingConfig 도메인 모델 - 사용자 거래 설정
 */
data class TradingConfig(
    val id: Long? = null,
    val userId: Long,
    val enabled: Boolean = false,
    val autoTradingEnabled: Boolean = false,
    val minCompositeScore: BigDecimal = BigDecimal("2.0"),
    val maxStocksToBuy: Int = 5,
    val maxAmountPerStock: BigDecimal = BigDecimal("10000.0"),
    val stopLossPercent: BigDecimal = BigDecimal("-7.0"),
    val takeProfitPercent: BigDecimal = BigDecimal("5.0"),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(maxStocksToBuy >= 1) { "maxStocksToBuy must be at least 1" }
        require(maxAmountPerStock > BigDecimal.ZERO) { "maxAmountPerStock must be positive" }
    }

    /**
     * 자동 매매 활성화 여부
     */
    fun isAutoTradingActive(): Boolean = enabled && autoTradingEnabled

    /**
     * 자동 매매 활성화
     */
    fun enableAutoTrading(): TradingConfig {
        return copy(
            enabled = true,
            autoTradingEnabled = true,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 자동 매매 비활성화
     */
    fun disableAutoTrading(): TradingConfig {
        return copy(
            autoTradingEnabled = false,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 최소 신뢰도를 0-1 범위로 변환
     */
    fun getMinConfidenceAsDecimal(): Double = minCompositeScore.toDouble() / 100.0
}
