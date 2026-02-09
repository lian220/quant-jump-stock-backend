package com.quantjumpstock.core.adapter.output.persistence.mongodb

import com.quantjumpstock.core.domain.StockPrediction
import java.time.LocalDateTime
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface StockPredictionMongoRepository : MongoRepository<StockPrediction, String> {

    /**
     * 티커로 예측 결과 조회
     */
    fun findByTicker(ticker: String): List<StockPrediction>

    /**
     * 티커와 날짜로 예측 결과 조회
     */
    fun findByTickerAndDate(ticker: String, date: LocalDateTime): StockPrediction?

    /**
     * 날짜 범위로 예측 결과 조회
     */
    fun findByDateBetween(startDate: LocalDateTime, endDate: LocalDateTime): List<StockPrediction>

    /**
     * 티커와 날짜 범위로 예측 결과 조회
     */
    fun findByTickerAndDateBetween(
        ticker: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<StockPrediction>
}
