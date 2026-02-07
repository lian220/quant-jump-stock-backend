package com.quantjumpstock.core.domain.model.portfolio

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 전략 기본 종목 도메인 모델
 * PORTFOLIO 타입 전략의 기본 구성 종목
 */
data class StrategyDefaultStock(
    val id: Long? = null,
    val strategyId: Long,
    val stockId: Long,
    val targetWeight: BigDecimal = BigDecimal.ZERO,
    val memo: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(targetWeight >= BigDecimal.ZERO && targetWeight <= BigDecimal(100)) {
            "Target weight must be between 0 and 100"
        }
    }
}
