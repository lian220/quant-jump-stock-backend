package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp

@Entity
@Table(name = "user_tiers")
class UserTierEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: UserEntity,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var tier: UserTier = UserTier.FREE,

    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null,

    @Column(name = "backtest_count_today")
    var backtestCountToday: Int = 0,

    @Column(name = "backtest_count_reset_at")
    var backtestCountResetAt: LocalDate = LocalDate.now(),

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun canPerformBacktest(): Boolean {
        resetDailyCountIfNeeded()
        return when (tier) {
            UserTier.FREE -> backtestCountToday < FREE_DAILY_LIMIT
            UserTier.PREMIUM, UserTier.PREMIUM_YEARLY -> true
        }
    }

    fun incrementBacktestCount() {
        resetDailyCountIfNeeded()
        backtestCountToday++
    }

    fun getRemainingBacktests(): Int {
        if (tier != UserTier.FREE) return Int.MAX_VALUE
        resetDailyCountIfNeeded()
        return (FREE_DAILY_LIMIT - backtestCountToday).coerceAtLeast(0)
    }

    fun isPremiumActive(): Boolean {
        if (tier == UserTier.FREE) return false
        val expires = expiresAt ?: return false
        return expires.isAfter(LocalDateTime.now())
    }

    private fun resetDailyCountIfNeeded() {
        val today = LocalDate.now()
        if (backtestCountResetAt != today) {
            backtestCountToday = 0
            backtestCountResetAt = today
        }
    }

    companion object {
        const val FREE_DAILY_LIMIT = 3
    }
}

enum class UserTier {
    FREE,
    PREMIUM,
    PREMIUM_YEARLY
}
