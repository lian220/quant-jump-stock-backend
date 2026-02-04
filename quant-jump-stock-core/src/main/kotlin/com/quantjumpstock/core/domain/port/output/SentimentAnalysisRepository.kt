package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.SentimentAnalysis

/**
 * SentimentAnalysis Repository Port
 * 도메인 레이어에서 정의하는 출력 포트
 */
interface SentimentAnalysisRepository {
    fun findAll(): List<SentimentAnalysis>
    fun findByTicker(ticker: String): List<SentimentAnalysis>
    fun findByTickerAndDate(ticker: String, date: String): SentimentAnalysis?
    fun save(analysis: SentimentAnalysis): SentimentAnalysis
}
