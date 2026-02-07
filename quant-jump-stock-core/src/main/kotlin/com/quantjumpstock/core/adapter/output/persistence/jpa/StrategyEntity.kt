package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import java.math.BigDecimal
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "strategies")
@NamedEntityGraph(
    name = "Strategy.withBacktestResults",
    attributeNodes = [
        NamedAttributeNode("backtestResults"),
        NamedAttributeNode("category")
    ]
)
data class StrategyEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    val category: StrategyCategoryEntity,

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_settings", columnDefinition = "jsonb")
    val riskSettings: String = "{}",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "position_sizing", columnDefinition = "jsonb")
    val positionSizing: String = "{}",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trading_costs", columnDefinition = "jsonb")
    val tradingCosts: String = "{}",

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
    @BatchSize(size = 20)
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

enum class StrategyStatus {
    DRAFT,           // 초안 - 사용자가 생성 후 아직 제출하지 않음
    PENDING_REVIEW,  // 검토 대기 - 관리자 승인 대기 중
    APPROVED,        // 승인됨 - 관리자가 승인, 발행 가능
    PUBLISHED,       // 발행됨 - 마켓플레이스에 공개
    REJECTED,        // 반려됨 - 관리자가 반려
    ACTIVE,          // 활성 (레거시 호환)
    ARCHIVED         // 보관됨 - 비활성화
}

enum class RebalanceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY,
    NONE
}
