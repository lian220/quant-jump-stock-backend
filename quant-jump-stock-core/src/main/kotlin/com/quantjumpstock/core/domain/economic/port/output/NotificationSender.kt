package com.quantjumpstock.core.domain.economic.port.output

/**
 * 알림 전송 인터페이스 (Output Port)
 * Slack과 같은 알림 서비스에 메시지를 전송하는 인터페이스입니다.
 */
interface NotificationSender {
    /**
     * 경제 데이터 업데이트 요청 알림
     *
     * @param requestId 요청 ID
     * @param startDate 수집 시작 날짜 (yyyy-MM-dd), null이면 자동 결정
     * @param endDate 수집 종료 날짜 (yyyy-MM-dd), null이면 오늘
     * @return Slack 스레드 타임스탬프 (답글용)
     */
    fun notifyEconomicDataUpdateRequest(requestId: String, startDate: String? = null, endDate: String? = null): String?

    /**
     * 경제 데이터 수집 오류 알림
     */
    fun notifyEconomicDataCollectionError(requestId: String, error: String)

    /**
     * 기술적 분석 요청 알림 (스레드 루트 메시지)
     * @param requestId 요청 ID
     * @param startDate 분석 시작 날짜 (yyyy-MM-dd), null이면 자동 결정
     * @param endDate 분석 종료 날짜 (yyyy-MM-dd), null이면 오늘
     * @return Slack 스레드 타임스탬프 (threadTs)
     */
    fun notifyTechnicalAnalysisRequest(requestId: String, startDate: String? = null, endDate: String? = null): String?

    /**
     * 감정 분석 요청 알림 (스레드 루트 메시지)
     * @param requestId 요청 ID
     * @param startDate 분석 시작 날짜 (yyyy-MM-dd), null이면 자동 결정
     * @param endDate 분석 종료 날짜 (yyyy-MM-dd), null이면 오늘
     * @return Slack 스레드 타임스탬프 (threadTs)
     */
    fun notifySentimentAnalysisRequest(requestId: String, startDate: String? = null, endDate: String? = null): String?

    /**
     * 종목 추천 요청 알림 (스레드 루트 메시지)
     * @param requestId 요청 ID
     * @param startDate 분석 시작 날짜 (yyyy-MM-dd), null이면 자동 결정
     * @param endDate 분석 종료 날짜 (yyyy-MM-dd), null이면 오늘
     * @return Slack 스레드 타임스탬프 (threadTs)
     */
    fun notifyStockRecommendationRequest(requestId: String, startDate: String? = null, endDate: String? = null): String?

    /**
     * 분석 오류 알림
     */
    fun notifyAnalysisError(requestId: String, analysisType: String, error: String)
}
