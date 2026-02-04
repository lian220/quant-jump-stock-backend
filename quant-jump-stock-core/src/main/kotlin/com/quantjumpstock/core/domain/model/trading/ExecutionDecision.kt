package com.quantjumpstock.core.domain.model.trading

/**
 * 실행 결정 - 도메인 Enum
 */
enum class ExecutionDecision {
    EXECUTED,  // 실행됨
    SKIPPED,   // 건너뜀
    FAILED     // 실패
}
