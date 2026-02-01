package com.quantjumpstock.core.domain.economic.port.output

import java.util.concurrent.CompletableFuture

/**
 * REST API 클라이언트 인터페이스 (Output Port)
 * 외부 REST API를 호출하는 인터페이스입니다.
 */
interface RestApiClient {
    /**
     * 경제 데이터 수집 API 호출
     * @param url API 엔드포인트 URL
     * @param targetDate 수집할 기준 날짜 (YYYY-MM-DD). null이면 당일 기준
     * @return 응답 결과
     */
    fun callEconomicDataCollectionApi(url: String, targetDate: String? = null): CompletableFuture<String>
}
