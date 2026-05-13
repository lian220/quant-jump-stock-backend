package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeSide
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface TradeJpaRepository : JpaRepository<TradeEntity, Long> {

    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<TradeEntity>

    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<TradeEntity>

    fun findByUserIdAndStatus(userId: Long, status: TradeStatus): List<TradeEntity>

    fun findByUserIdAndTicker(userId: Long, ticker: String): List<TradeEntity>

    fun findByStatus(status: TradeStatus): List<TradeEntity>

    /**
     * Stale PENDING trade 조회 — `status = PENDING AND updatedAt < :threshold`.
     * AFTER_COMMIT 리스너 보상 실패로 영구 PENDING 에 남은 trade 회수용.
     */
    @Query("SELECT t FROM TradeEntity t WHERE t.status = 'PENDING' AND t.updatedAt < :threshold")
    fun findStalePending(threshold: LocalDateTime): List<TradeEntity>

    @Query("""
        SELECT t FROM TradeEntity t
        WHERE t.user.id = :userId
        AND t.createdAt BETWEEN :startDate AND :endDate
        ORDER BY t.createdAt DESC
    """)
    fun findByUserIdAndDateRange(
        userId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<TradeEntity>

    @Query("""
        SELECT t FROM TradeEntity t
        WHERE t.user.id = :userId
        AND t.ticker = :ticker
        AND t.side = :side
        AND t.status = :status
        AND t.createdAt >= :since
    """)
    fun findRecentTrade(
        userId: Long,
        ticker: String,
        side: TradeSide,
        status: TradeStatus,
        since: LocalDateTime
    ): List<TradeEntity>

    @Modifying
    @Query("""
        UPDATE TradeEntity t
        SET t.status = :status, t.executedAt = :executedAt, t.kisOrderId = :kisOrderId, t.updatedAt = CURRENT_TIMESTAMP
        WHERE t.id = :tradeId
    """)
    fun updateTradeStatus(
        tradeId: Long,
        status: TradeStatus,
        executedAt: LocalDateTime?,
        kisOrderId: String?
    ): Int

    @Query("SELECT COUNT(t) FROM TradeEntity t WHERE t.user.id = :userId AND t.status = :status")
    fun countByUserIdAndStatus(userId: Long, status: TradeStatus): Long

    @Query("""
        SELECT t.ticker, COUNT(t) as tradeCount
        FROM TradeEntity t
        WHERE t.user.id = :userId
        GROUP BY t.ticker
        ORDER BY tradeCount DESC
    """)
    fun getTradeCountByTicker(userId: Long): List<Array<Any>>

    fun findByUserId(userId: Long): List<TradeEntity>

    fun findByTicker(ticker: String): List<TradeEntity>

    fun findByUserIdAndCreatedAtBetween(
        userId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<TradeEntity>
}
