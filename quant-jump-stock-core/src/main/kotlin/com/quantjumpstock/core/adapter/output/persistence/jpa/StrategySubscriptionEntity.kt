package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.quantjumpstock.core.domain.model.backtest.UniverseType
import jakarta.persistence.*
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp

@Entity
@Table(
    name = "strategy_subscriptions",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_strategy_subscriptions_user_strategy",
            columnNames = ["user_id", "strategy_id"]
        )
    ]
)
class StrategySubscriptionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id", nullable = false)
    val strategy: StrategyEntity,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var status: SubscriptionStatus = SubscriptionStatus.ACTIVE,

    @Column(name = "notify_signals")
    var notifySignals: Boolean = true,

    @Column(name = "notify_rebalance")
    var notifyRebalance: Boolean = true,

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_universe_type", length = 20, nullable = false)
    var preferredUniverseType: UniverseType = UniverseType.MARKET,

    @Column(name = "subscribed_at")
    val subscribedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "cancelled_at")
    var cancelledAt: LocalDateTime? = null,

    /**
     * 본 구독 전략 실행 시 사용할 user_broker_accounts.id (Phase 1B v2.1).
     * NULL = 사용자의 활성 계좌 중 첫 번째 자동 선택 (legacy fallback).
     * FK 가 ON DELETE SET NULL — 계좌 hard delete 시 자동 NULL 처리.
     */
    @Column(name = "broker_account_id")
    var brokerAccountId: Long? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class SubscriptionStatus {
    ACTIVE,
    PAUSED,
    CANCELLED
}
