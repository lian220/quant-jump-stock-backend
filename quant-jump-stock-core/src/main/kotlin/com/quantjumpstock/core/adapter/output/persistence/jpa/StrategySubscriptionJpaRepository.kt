package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface StrategySubscriptionJpaRepository : JpaRepository<StrategySubscriptionEntity, Long> {

    fun findByUserId(userId: Long): List<StrategySubscriptionEntity>
    fun findByUserId(userId: Long, pageable: Pageable): Page<StrategySubscriptionEntity>

    fun findByStrategyId(strategyId: Long): List<StrategySubscriptionEntity>
    fun findByStrategyId(strategyId: Long, pageable: Pageable): Page<StrategySubscriptionEntity>

    fun findByStatus(status: SubscriptionStatus, pageable: Pageable): Page<StrategySubscriptionEntity>

    fun findByUserIdAndStatus(userId: Long, status: SubscriptionStatus): List<StrategySubscriptionEntity>

    fun findByUserIdAndStrategyId(userId: Long, strategyId: Long): Optional<StrategySubscriptionEntity>

    fun existsByUserIdAndStrategyIdAndStatus(userId: Long, strategyId: Long, status: SubscriptionStatus): Boolean

    // 활성 구독 조회 (전략 정보 포함)
    @Query("""
        SELECT ss FROM StrategySubscriptionEntity ss
        JOIN FETCH ss.strategy s
        WHERE ss.user.id = :userId AND ss.status = 'ACTIVE'
        ORDER BY ss.subscribedAt DESC
    """)
    fun findActiveSubscriptionsWithStrategy(userId: Long): List<StrategySubscriptionEntity>

    @Query("""
        SELECT ss FROM StrategySubscriptionEntity ss
        JOIN FETCH ss.strategy s
        WHERE ss.user.id = :userId AND ss.status = 'ACTIVE'
    """)
    fun findActiveSubscriptionsWithStrategy(userId: Long, pageable: Pageable): Page<StrategySubscriptionEntity>

    // 알림 대상 구독자
    @Query("""
        SELECT ss FROM StrategySubscriptionEntity ss
        JOIN FETCH ss.user u
        WHERE ss.strategy.id = :strategyId
        AND ss.status = 'ACTIVE'
        AND ss.notifySignals = true
    """)
    fun findSubscribersForSignalNotification(strategyId: Long): List<StrategySubscriptionEntity>

    @Query("""
        SELECT ss FROM StrategySubscriptionEntity ss
        JOIN FETCH ss.user u
        WHERE ss.strategy.id = :strategyId
        AND ss.status = 'ACTIVE'
        AND ss.notifyRebalance = true
    """)
    fun findSubscribersForRebalanceNotification(strategyId: Long): List<StrategySubscriptionEntity>

    // 구독 상태 변경
    @Modifying
    @Query("""
        UPDATE StrategySubscriptionEntity ss
        SET ss.status = :status, ss.cancelledAt = :cancelledAt
        WHERE ss.id = :subscriptionId
    """)
    fun updateStatus(subscriptionId: Long, status: SubscriptionStatus, cancelledAt: LocalDateTime?): Int

    // 통계
    @Query("SELECT COUNT(ss) FROM StrategySubscriptionEntity ss WHERE ss.user.id = :userId AND ss.status = 'ACTIVE'")
    fun countActiveByUserId(userId: Long): Long

    @Query("SELECT COUNT(ss) FROM StrategySubscriptionEntity ss WHERE ss.strategy.id = :strategyId AND ss.status = 'ACTIVE'")
    fun countActiveByStrategyId(strategyId: Long): Long

    @Query("SELECT ss.user.id, COUNT(ss) FROM StrategySubscriptionEntity ss WHERE ss.user.id IN :userIds AND ss.status = 'ACTIVE' GROUP BY ss.user.id")
    fun countActiveByUserIdIn(userIds: List<Long>): List<Array<Any>>
}
