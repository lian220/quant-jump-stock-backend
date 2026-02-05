package com.quantjumpstock.core.domain.analysis.port.input

import java.util.concurrent.CompletableFuture

/**
 * 분석 UseCase (Input Port)
 * 기술적 분석, 감정 분석을 트리거하는 비즈니스 로직
 */
interface AnalysisUseCase {

    /**
     * 기술적 분석 트리거
     * SMA, RSI, MACD 등 기술적 지표 분석 요청
     * @param targetDate 분석 대상 날짜 (yyyy-MM-dd), null이면 당일
     */
    fun triggerTechnicalAnalysis(targetDate: String? = null): CompletableFuture<String>

    /**
     * 뉴스 감정 분석 트리거
     * Alpha Vantage NEWS_SENTIMENT API를 통한 감정 분석 요청
     * @param targetDate 분석 대상 날짜 (yyyy-MM-dd), null이면 당일
     */
    fun triggerSentimentAnalysis(targetDate: String? = null): CompletableFuture<String>
}
