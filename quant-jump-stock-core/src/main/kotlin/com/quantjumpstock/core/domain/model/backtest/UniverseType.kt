package com.quantjumpstock.core.domain.model.backtest

/**
 * 백테스트 유니버스 타입
 * 백테스트 실행 시 대상 종목 선택 방식
 */
enum class UniverseType {
    /** 전체 시장 (stocks 테이블 전체) */
    MARKET,
    /** 전략 기본 포트폴리오 종목 */
    PORTFOLIO,
    /** 섹터별 종목 (향후 확장) */
    SECTOR,
    /** 사용자 지정 종목 */
    FIXED
}
