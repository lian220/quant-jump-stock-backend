package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "strategies")
data class StrategyEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val category: StrategyCategory,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    val owner: UserEntity? = null,

    @Column(name = "is_public")
    val isPublic: Boolean = false,

    @Column(name = "is_premium")
    val isPremium: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var status: StrategyStatus = StrategyStatus.DRAFT,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    val conditions: String = "{}",

    @Enumerated(EnumType.STRING)
    @Column(name = "rebalance_frequency", length = 20)
    val rebalanceFrequency: RebalanceFrequency = RebalanceFrequency.MONTHLY,

    @Column(name = "subscriber_count")
    var subscriberCount: Int = 0,

    @Column(name = "average_rating", precision = 3, scale = 2)
    var averageRating: BigDecimal = BigDecimal.ZERO,

    @OneToMany(mappedBy = "strategy", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val subscriptions: MutableList<StrategySubscriptionEntity> = mutableListOf(),

    @OneToMany(mappedBy = "strategy", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val backtestResults: MutableList<BacktestResultEntity> = mutableListOf(),

    @OneToMany(mappedBy = "strategy", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val signals: MutableList<StrategySignalEntity> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class StrategyCategory {
    VALUE,
    MOMENTUM,
    ASSET_ALLOCATION,
    QUANT_COMPOSITE,
    SEASONAL,
    CUSTOM
}

enum class StrategyStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}

enum class RebalanceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY,
    NONE
}
