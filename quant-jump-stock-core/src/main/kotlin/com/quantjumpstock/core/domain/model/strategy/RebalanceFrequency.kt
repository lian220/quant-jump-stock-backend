package com.quantjumpstock.core.domain.model.strategy

/**
 * 리밸런싱 주기 - 순수 도메인 Enum
 */
enum class RebalanceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY,
    NONE
}
