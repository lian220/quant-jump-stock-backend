package com.quantjumpstock.core.domain.model.portfolio

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 포트폴리오 종목 도메인 모델
 */
data class PortfolioStock(
    val id: Long? = null,
    val portfolioId: Long,
    val stockId: Long,
    val targetWeight: BigDecimal = BigDecimal.ZERO,
    val isFromStrategy: Boolean = false,
    val memo: String? = null,
    val addedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(targetWeight >= BigDecimal.ZERO && targetWeight <= BigDecimal(100)) {
            "Target weight must be between 0 and 100"
        }
    }
}
