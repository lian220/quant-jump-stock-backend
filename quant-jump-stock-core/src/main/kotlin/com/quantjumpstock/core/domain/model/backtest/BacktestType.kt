package com.quantjumpstock.core.domain.model.backtest

/**
 * 백테스트 유형
 * 시스템 자동 실행(Canonical) vs 사용자 커스텀 실행 구분
 */
enum class BacktestType {
    /** 시스템이 자동으로 실행하는 대표 백테스트 (표준 파라미터) */
    CANONICAL,
    /** 사용자가 커스텀 파라미터로 실행하는 백테스트 */
    USER_CUSTOM
}
