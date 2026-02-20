package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.quantjumpstock.core.domain.model.tier.TierConfiguration
import jakarta.persistence.*
import java.time.LocalDateTime
import org.hibernate.annotations.UpdateTimestamp

@Entity
@Table(name = "tier_configurations")
class TierConfigurationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(length = 20, nullable = false, unique = true)
    var tier: String,

    @Column(name = "max_subscription_count")
    var maxSubscriptionCount: Int = 3,

    @Column(name = "max_backtest_daily")
    var maxBacktestDaily: Int = 3,

    @Column(name = "max_backtest_per_strategy")
    var maxBacktestPerStrategy: Int = 5,

    @Column(name = "is_unlimited_subscription")
    var isUnlimitedSubscription: Boolean = false,

    @Column(name = "is_unlimited_backtest")
    var isUnlimitedBacktest: Boolean = false,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_by", length = 100)
    var updatedBy: String? = null
) {
    fun toDomain(): TierConfiguration = TierConfiguration(
        id = id,
        tier = tier,
        maxSubscriptionCount = maxSubscriptionCount,
        maxBacktestDaily = maxBacktestDaily,
        maxBacktestPerStrategy = maxBacktestPerStrategy,
        isUnlimitedSubscription = isUnlimitedSubscription,
        isUnlimitedBacktest = isUnlimitedBacktest,
        description = description,
        updatedAt = updatedAt,
        updatedBy = updatedBy
    )

    fun updateFrom(domain: TierConfiguration) {
        maxSubscriptionCount = domain.maxSubscriptionCount
        maxBacktestDaily = domain.maxBacktestDaily
        maxBacktestPerStrategy = domain.maxBacktestPerStrategy
        isUnlimitedSubscription = domain.isUnlimitedSubscription
        isUnlimitedBacktest = domain.isUnlimitedBacktest
        description = domain.description
        updatedBy = domain.updatedBy
    }
}
