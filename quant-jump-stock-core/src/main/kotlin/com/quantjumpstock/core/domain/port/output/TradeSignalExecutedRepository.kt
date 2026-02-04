package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.trading.TradeSignalExecuted
import java.time.LocalDateTime

/**
 * TradeSignalExecuted Repository Port - 도메인 포트
 */
interface TradeSignalExecutedRepository {

    fun save(signal: TradeSignalExecuted): TradeSignalExecuted

    fun findById(id: Long): TradeSignalExecuted?

    fun findByUserId(userId: Long): List<TradeSignalExecuted>

    fun findByUserIdAndTicker(userId: Long, ticker: String): List<TradeSignalExecuted>

    fun findByUserIdAndCreatedAtAfter(userId: Long, after: LocalDateTime): List<TradeSignalExecuted>

    fun deleteById(id: Long)
}
