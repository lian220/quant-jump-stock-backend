package com.quantjumpstock.core.domain.economic.port.input

import java.util.concurrent.CompletableFuture

/**
 * 경제 데이터 업데이트 Use Case (Input Port)
 * 경제 데이터 수집 트리거 비즈니스 로직의 인터페이스를 정의합니다.
 */
interface EconomicDataUseCase {
    /**
     * 경제 데이터 업데이트 트리거
     * Pub/Sub 메시지를 발행하여 데이터 수집을 요청합니다.
     *
     * @param startDate 수집 시작 날짜 (YYYY-MM-DD). null이면 Data Engine이 마지막 수집일+1부터 결정
     * @param endDate 수집 종료 날짜 (YYYY-MM-DD). null이면 오늘
     */
    fun triggerEconomicDataUpdate(startDate: String? = null, endDate: String? = null): CompletableFuture<String>

    /**
     * REST API를 통한 경제 데이터 업데이트 (대안 구현)
     *
     * @param startDate 수집 시작 날짜 (YYYY-MM-DD). null이면 당일 기준
     * @param endDate 수집 종료 날짜 (YYYY-MM-DD). null이면 오늘
     */
    fun triggerEconomicDataUpdateViaRestApi(startDate: String? = null, endDate: String? = null): CompletableFuture<String>
}
