package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.StockAnalysis

/**
 * StockAnalysis Repository Port
 * 도메인 레이어에서 정의하는 출력 포트
 */
interface StockAnalysisRepository {
    fun findAll(): List<StockAnalysis>
    fun findByTicker(ticker: String): List<StockAnalysis>
    fun save(analysis: StockAnalysis): StockAnalysis
}
