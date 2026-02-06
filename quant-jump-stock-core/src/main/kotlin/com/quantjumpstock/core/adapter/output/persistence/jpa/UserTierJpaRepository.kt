package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface UserTierJpaRepository : JpaRepository<UserTierEntity, Long> {

    fun findByUserId(userId: Long): Optional<UserTierEntity>

    fun findByTier(tier: UserTier): List<UserTierEntity>

    fun existsByUserId(userId: Long): Boolean

    // 프리미엄 사용자 조회
    @Query("""
        SELECT ut FROM UserTierEntity ut
        WHERE ut.tier IN ('PREMIUM', 'PREMIUM_YEARLY')
        AND ut.expiresAt > CURRENT_TIMESTAMP
    """)
    fun findActivePremiumUsers(): List<UserTierEntity>

    // 만료 예정 구독
    @Query("""
        SELECT ut FROM UserTierEntity ut
        WHERE ut.tier IN ('PREMIUM', 'PREMIUM_YEARLY')
        AND ut.expiresAt BETWEEN :now AND :until
    """)
    fun findExpiringSubscriptions(now: LocalDateTime, until: LocalDateTime): List<UserTierEntity>

    // 만료된 구독 (자동 갱신 실패 등)
    @Query("""
        SELECT ut FROM UserTierEntity ut
        WHERE ut.tier IN ('PREMIUM', 'PREMIUM_YEARLY')
        AND ut.expiresAt < CURRENT_TIMESTAMP
    """)
    fun findExpiredSubscriptions(): List<UserTierEntity>

    // 티어 업그레이드
    @Modifying
    @Query("""
        UPDATE UserTierEntity ut
        SET ut.tier = :tier, ut.startedAt = :startedAt, ut.expiresAt = :expiresAt
        WHERE ut.user.id = :userId
    """)
    fun upgradeTier(userId: Long, tier: UserTier, startedAt: LocalDateTime, expiresAt: LocalDateTime): Int

    // 티어 다운그레이드 (FREE로)
    @Modifying
    @Query("""
        UPDATE UserTierEntity ut
        SET ut.tier = 'FREE', ut.expiresAt = null
        WHERE ut.user.id = :userId
    """)
    fun downgradeToFree(userId: Long): Int

    // 일일 백테스트 카운트 리셋
    @Modifying
    @Query("""
        UPDATE UserTierEntity ut
        SET ut.backtestCountToday = 0, ut.backtestCountResetAt = :today
        WHERE ut.backtestCountResetAt < :today
    """)
    fun resetDailyBacktestCounts(today: LocalDate): Int

    // 백테스트 카운트 증가
    @Modifying
    @Query("""
        UPDATE UserTierEntity ut
        SET ut.backtestCountToday = ut.backtestCountToday + 1
        WHERE ut.user.id = :userId
    """)
    fun incrementBacktestCount(userId: Long): Int

    // 원자적 조건부 카운트 증가 (TOCTOU 방지)
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE UserTierEntity ut
        SET ut.backtestCountToday = ut.backtestCountToday + 1
        WHERE ut.user.id = :userId
        AND ut.backtestCountToday < :limit
    """)
    fun incrementBacktestCountIfBelowLimit(userId: Long, limit: Int): Int

    // 통계
    @Query("SELECT COUNT(ut) FROM UserTierEntity ut WHERE ut.tier = :tier")
    fun countByTier(tier: UserTier): Long

    @Query("""
        SELECT ut.tier, COUNT(ut)
        FROM UserTierEntity ut
        GROUP BY ut.tier
    """)
    fun getTierDistribution(): List<Array<Any>>
}
