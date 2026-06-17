package com.quantjumpstock.core.application.stock

/**
 * 종목 가격 이력 조회 포트 (Output Port)
 *
 * Application 레이어가 data-engine 같은 외부 어댑터에 직접 의존하지 않도록
 * Hexagonal 경계를 분리한다. 구현체(DataEngineClient)는 adapter/output 레이어에 위치한다.
 *
 * 응답 계약(PriceHistoryResponse)이 application DTO 이므로 포트도 application 레이어에 둔다.
 */
interface PriceHistoryPort {
    fun getPriceHistory(ticker: String, period: String): PriceHistoryResponse
}
