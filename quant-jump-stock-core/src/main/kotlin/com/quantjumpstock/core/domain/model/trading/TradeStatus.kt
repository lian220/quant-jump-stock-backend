package com.quantjumpstock.core.domain.model.trading

/**
 * 거래 상태 - 순수 도메인 Enum
 */
enum class TradeStatus {
    PENDING,    // 대기 중
    EXECUTED,   // 체결됨
    FAILED,     // 실패
    CANCELLED;  // 취소됨

    fun canTransitionTo(newStatus: TradeStatus): Boolean = when (this) {
        PENDING -> newStatus in listOf(EXECUTED, FAILED, CANCELLED)
        EXECUTED -> false  // 체결된 거래는 상태 변경 불가
        FAILED -> false    // 실패한 거래는 상태 변경 불가
        CANCELLED -> false // 취소된 거래는 상태 변경 불가
    }

    fun isFinal(): Boolean = this in listOf(EXECUTED, FAILED, CANCELLED)
}
