package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.quantjumpstock.core.adapter.output.persistence.jpa.ExecutionDecision
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeSignalExecutedEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface TradeSignalExecutedJpaRepository : JpaRepository<TradeSignalExecutedEntity, Long> {

    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<TradeSignalExecutedEntity>

    fun findByRecommendationId(recommendationId: String): List<TradeSignalExecutedEntity>

    @Query("""
        SELECT tse FROM TradeSignalExecutedEntity tse
        WHERE tse.user.id = :userId
        AND tse.ticker = :ticker
        AND tse.createdAt >= :since
    """)
    fun findRecentSignalsForTicker(
        userId: Long,
        ticker: String,
        since: LocalDateTime
    ): List<TradeSignalExecutedEntity>

    @Query("""
        SELECT tse.executionDecision, COUNT(tse)
        FROM TradeSignalExecutedEntity tse
        WHERE tse.user.id = :userId
        GROUP BY tse.executionDecision
    """)
    fun getExecutionStats(userId: Long): List<Array<Any>>

    fun existsByUserIdAndRecommendationId(userId: Long, recommendationId: String): Boolean

    @Query("""
        SELECT COUNT(tse) FROM TradeSignalExecutedEntity tse
        WHERE tse.user.id = :userId
        AND tse.executionDecision = :decision
        AND tse.createdAt >= :since
    """)
    fun countByDecisionSince(
        userId: Long,
        decision: ExecutionDecision,
        since: LocalDateTime
    ): Long
}
