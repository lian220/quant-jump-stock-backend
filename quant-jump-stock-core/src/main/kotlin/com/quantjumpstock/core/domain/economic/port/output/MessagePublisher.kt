package com.quantjumpstock.core.domain.economic.port.output

import com.quantjumpstock.core.domain.model.AnalysisRequest
import com.quantjumpstock.core.domain.model.BacktestRequest
import com.quantjumpstock.core.domain.model.EconomicDataUpdateRequest
import com.quantjumpstock.core.domain.model.VertexAIPredictionRequest
/**
 * 메시지 발행 인터페이스 (Output Port)
 * Kafka와 같은 메시지 브로커에 이벤트를 발행하는 인터페이스입니다.
 */
interface MessagePublisher {
    /**
     * 경제 데이터 업데이트 요청 메시지 발행
     * @param topic 메시지 토픽
     * @param request 경제 데이터 업데이트 요청
     */
    fun publishEconomicDataUpdateRequest(
        topic: String,
        request: EconomicDataUpdateRequest
    )

    /**
     * 분석 요청 메시지 발행
     * @param topic 메시지 토픽
     * @param request 분석 요청
     */
    fun publishAnalysisRequest(
        topic: String,
        request: AnalysisRequest
    )

    /**
     * Vertex AI 예측 요청 메시지 발행 (Admin 긴급 수동 실행용)
     * @param topic 메시지 토픽
     * @param request Vertex AI 예측 요청
     */
    fun publishVertexAIPredictionRequest(
        topic: String,
        request: VertexAIPredictionRequest
    )

    /**
     * 백테스트 요청 메시지 발행
     * @param topic 메시지 토픽
     * @param request 백테스트 요청
     */
    fun publishBacktestRequest(
        topic: String,
        request: BacktestRequest
    )

    /**
     * 뉴스 수집 요청 메시지 발행
     * @param topic 메시지 토픽
     * @param requestId 요청 ID
     * @param source 뉴스 소스 (SAVETICKER 등)
     */
    fun publishNewsCollectionRequest(
        topic: String,
        requestId: String,
        source: String
    )
}
