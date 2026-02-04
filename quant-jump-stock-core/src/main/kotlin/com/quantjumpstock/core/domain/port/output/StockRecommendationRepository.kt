package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.StockRecommendation

/**
 * StockRecommendation Repository Port
 * 도메인 레이어에서 정의하는 출력 포트
 */
interface StockRecommendationRepository {
    fun findAll(): List<StockRecommendation>
    fun findByDateAndIsRecommendedTrue(date: String): List<StockRecommendation>
    fun findByTickerAndDate(ticker: String, date: String): StockRecommendation?
    fun save(recommendation: StockRecommendation): StockRecommendation
}
