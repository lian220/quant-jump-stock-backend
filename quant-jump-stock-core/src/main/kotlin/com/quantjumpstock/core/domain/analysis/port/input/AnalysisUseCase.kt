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
     * @param startDate 분석 시작 날짜 (yyyy-MM-dd), null이면 Data Engine이 결정
     * @param endDate 분석 종료 날짜 (yyyy-MM-dd), null이면 오늘
     * @param source 요청 출처 ("standalone" | "pipeline"). standalone=단독실행, pipeline=체이닝
     */
    fun triggerTechnicalAnalysis(startDate: String? = null, endDate: String? = null, source: String = "standalone"): CompletableFuture<String>

    /**
     * 뉴스 감정 분석 트리거
     * Alpha Vantage NEWS_SENTIMENT API를 통한 감정 분석 요청
     * @param startDate 분석 시작 날짜 (yyyy-MM-dd), null이면 Data Engine이 결정
     * @param endDate 분석 종료 날짜 (yyyy-MM-dd), null이면 오늘
     * @param source 요청 출처 ("standalone" | "pipeline"). standalone=단독실행, pipeline=체이닝
     */
    fun triggerSentimentAnalysis(startDate: String? = null, endDate: String? = null, source: String = "standalone"): CompletableFuture<String>

    /**
     * 종목 추천 트리거
     * Composite Score 계산: AI(30%) + Technical(40%) + Sentiment(30%)
     * @param startDate 분석 시작 날짜 (yyyy-MM-dd), null이면 Data Engine이 결정
     * @param endDate 분석 종료 날짜 (yyyy-MM-dd), null이면 오늘
     * @param source 요청 출처 ("standalone" | "pipeline"). standalone=단독실행, pipeline=체이닝
     */
    fun triggerStockRecommendation(startDate: String? = null, endDate: String? = null, source: String = "standalone"): CompletableFuture<String>
}
