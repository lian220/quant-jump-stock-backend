package com.quantjumpstock.core.application.admin

import com.quantjumpstock.core.domain.port.output.StrategySubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class AdminSubscriptionService(
    private val subscriptionRepository: StrategySubscriptionRepository
) {

    fun getSubscriptions(
        strategyId: Long?,
        userId: Long?,
        page: Int,
        size: Int
    ): AdminSubscriptionListResult {
        val adminPage = subscriptionRepository.findAdminSubscriptions(strategyId, userId, page, size)
        val items = adminPage.items.map { info ->
            AdminSubscriptionItemResult(
                subscriptionId = info.subscriptionId,
                userId = info.userDbId,
                userLoginId = info.userLoginId,
                strategyId = info.strategyId,
                strategyName = info.strategyName,
                status = info.status,
                alertEnabled = info.alertEnabled,
                subscribedAt = info.subscribedAt
            )
        }
        return AdminSubscriptionListResult(
            subscriptions = items,
            total = adminPage.total,
            activeCount = adminPage.activeCount
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
