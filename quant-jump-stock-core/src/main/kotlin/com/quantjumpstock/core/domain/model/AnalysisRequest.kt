package com.quantjumpstock.core.domain.model

/**
 * 분석 요청 Domain Model
 */
data class AnalysisRequest(
    val timestamp: String,
    val source: String,
    val requestId: String,
    val threadTs: String? = null,
    val analysisType: String, // "TECHNICAL", "SENTIMENT", "COMBINED"
    val targetDate: String? = null // 분석 대상 날짜 (yyyy-MM-dd)
)
