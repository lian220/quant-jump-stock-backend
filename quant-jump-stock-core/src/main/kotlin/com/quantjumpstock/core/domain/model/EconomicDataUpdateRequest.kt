package com.quantjumpstock.core.domain.model

/**
 * 경제 데이터 업데이트 요청 도메인 모델
 */
data class EconomicDataUpdateRequest(
    val timestamp: String,
    val source: String,
    val requestId: String,
    val threadTs: String? = null,  // Slack 스레드 타임스탬프 (답글용)
    val targetDate: String? = null  // 수집할 기준 날짜 (YYYY-MM-DD). null이면 당일 기준
) {
    override fun toString(): String {
        return """
            {
                "timestamp": "$timestamp",
                "source": "$source",
                "requestId": "$requestId",
                "threadTs": "$threadTs",
                "targetDate": "$targetDate"
            }
        """.trimIndent()
    }
}
