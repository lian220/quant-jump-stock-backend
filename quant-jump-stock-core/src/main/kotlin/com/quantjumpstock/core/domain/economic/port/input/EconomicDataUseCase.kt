package com.quantjumpstock.core.domain.economic.port.input

import java.util.concurrent.CompletableFuture

/**
 * 경제 데이터 업데이트 Use Case (Input Port)
 * 경제 데이터 수집 트리거 비즈니스 로직의 인터페이스를 정의합니다.
 */
interface EconomicDataUseCase {
    /**
     * 경제 데이터 업데이트 트리거
     * Kafka 이벤트를 발행하여 데이터 수집을 요청합니다.
     *
     * @param targetDate 수집할 기준 날짜 (YYYY-MM-DD). null이면 당일 기준
     */
    fun triggerEconomicDataUpdate(targetDate: String? = null): CompletableFuture<String>

    /**
     * REST API를 통한 경제 데이터 업데이트 (대안 구현)
     *
     * @param targetDate 수집할 기준 날짜 (YYYY-MM-DD). null이면 당일 기준
     */
    fun triggerEconomicDataUpdateViaRestApi(targetDate: String? = null): CompletableFuture<String>
}
