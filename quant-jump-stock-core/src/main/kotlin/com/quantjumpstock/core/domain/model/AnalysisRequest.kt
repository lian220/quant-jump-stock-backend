package com.quantjumpstock.core.domain.model

/**
 * 분석 요청 Domain Model
 */
data class AnalysisRequest(
    val timestamp: String,
    val source: String,
    val requestId: String,
    val threadTs: String? = null,
    val analysisType: String, // "TECHNICAL", "SENTIMENT"
    val startDate: String? = null, // 분석 시작 날짜 (yyyy-MM-dd)
    val endDate: String? = null    // 분석 종료 날짜 (yyyy-MM-dd)
)
