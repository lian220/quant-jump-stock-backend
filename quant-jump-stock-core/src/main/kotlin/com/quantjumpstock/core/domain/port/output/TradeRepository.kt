package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.trading.Trade
import com.quantjumpstock.core.domain.model.trading.TradeSide
import com.quantjumpstock.core.domain.model.trading.TradeStatus
import java.time.LocalDateTime

/**
 * Trade Repository Port - 도메인 포트
 */
interface TradeRepository {

    fun save(trade: Trade): Trade

    fun findById(id: Long): Trade?

    fun findByUserId(userId: Long): List<Trade>

    fun findByUserIdAndStatus(userId: Long, status: TradeStatus): List<Trade>

    fun findByTicker(ticker: String): List<Trade>

    fun findByUserIdAndTicker(userId: Long, ticker: String): List<Trade>

    fun findByUserIdAndCreatedAtBetween(
        userId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Trade>

    fun findPendingTrades(): List<Trade>

    /**
     * `updatedAt` 이 [threshold] 이전인 PENDING trade 조회 (stale).
     *
     * AFTER_COMMIT 리스너의 보상 자체가 실패(JVM crash, DB 일시 장애)했을 때
     * 영구 PENDING 으로 남는 trade 를 정기 스윕으로 회수하기 위함.
     * 호출자는 [com.quantjumpstock.core.application.trading.StalePendingTradeReconciler].
     */
    fun findStalePending(threshold: LocalDateTime): List<Trade>

    fun deleteById(id: Long)

    fun existsById(id: Long): Boolean

    /**
     * 최근 거래 조회 (특정 조건 기반)
     */
    fun findRecentTrades(
        userId: Long,
        ticker: String,
        side: TradeSide,
        status: TradeStatus,
        since: LocalDateTime
    ): List<Trade>
}
