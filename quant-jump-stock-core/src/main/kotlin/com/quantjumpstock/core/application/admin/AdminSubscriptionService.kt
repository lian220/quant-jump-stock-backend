package com.quantjumpstock.core.application.admin

import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategySubscriptionEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategySubscriptionJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.SubscriptionStatus
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class AdminSubscriptionService(
    private val jpaRepository: StrategySubscriptionJpaRepository
) {

    fun getSubscriptions(
        strategyId: Long?,
        userId: Long?,
        page: Int,
        size: Int
    ): AdminSubscriptionListResult {
        val safeSize = size.coerceIn(1, 100)
        val pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "subscribedAt"))

        val pagedResult = when {
            strategyId != null -> jpaRepository.findByStrategyId(strategyId, pageable)
            userId != null -> jpaRepository.findByUserId(userId, pageable)
            else -> jpaRepository.findByStatus(SubscriptionStatus.ACTIVE, pageable)
        }

        val items = pagedResult.content.map { it.toAdminSummary() }
        val activeCount = when {
            strategyId != null || userId != null -> items.count { it.status == "ACTIVE" }.toLong()
            else -> pagedResult.totalElements
        }

        return AdminSubscriptionListResult(
            subscriptions = items,
            total = pagedResult.totalElements,
            activeCount = activeCount
        )
    }
}

data class AdminSubscriptionItemResult(
    val subscriptionId: Long,
    val userId: Long,
    val userLoginId: String,
    val strategyId: Long,
    val strategyName: String,
    val status: String,
    val alertEnabled: Boolean,
    val subscribedAt: LocalDateTime
)

data class AdminSubscriptionListResult(
    val subscriptions: List<AdminSubscriptionItemResult>,
    val total: Long,
    val activeCount: Long
)

private fun StrategySubscriptionEntity.toAdminSummary() = AdminSubscriptionItemResult(
    subscriptionId = id ?: 0L,
    userId = user.id ?: 0L,
    userLoginId = user.userId,
    strategyId = strategy.id ?: 0L,
    strategyName = strategy.name,
    status = status.name,
    alertEnabled = notifySignals || notifyRebalance,
    subscribedAt = subscribedAt
)
