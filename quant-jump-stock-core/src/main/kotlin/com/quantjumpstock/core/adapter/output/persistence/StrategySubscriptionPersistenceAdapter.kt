package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategySubscriptionJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.SubscriptionStatus
import com.quantjumpstock.core.domain.model.backtest.UniverseType
import com.quantjumpstock.core.domain.port.output.StrategySubscriptionRepository
import com.quantjumpstock.core.domain.port.output.StrategySubscriptionView
import org.springframework.stereotype.Component

@Component
class StrategySubscriptionPersistenceAdapter(
    private val jpaRepository: StrategySubscriptionJpaRepository
) : StrategySubscriptionRepository {

    override fun findActiveByUserId(userId: Long): List<StrategySubscriptionView> {
        return jpaRepository
            .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
            .mapNotNull { entity ->
                val strategyId = entity.strategy.id ?: return@mapNotNull null
                StrategySubscriptionView(
                    strategyId = strategyId,
                    preferredUniverseType = entity.preferredUniverseType
                )
            }
    }
}
