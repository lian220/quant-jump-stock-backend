package com.quantjumpstock.core.adapter.output.persistence.jpa

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
data class StrategySubscriptionEntity(
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

    @Column(name = "subscribed_at")
    val subscribedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "cancelled_at")
    var cancelledAt: LocalDateTime? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class SubscriptionStatus {
    ACTIVE,
    PAUSED,
    CANCELLED
}
