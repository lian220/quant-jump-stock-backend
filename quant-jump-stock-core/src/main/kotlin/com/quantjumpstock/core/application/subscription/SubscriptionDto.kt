package com.quantjumpstock.core.application.subscription

import com.quantjumpstock.core.domain.model.backtest.UniverseType
import java.time.LocalDateTime

data class SubscribeRequest(
    val universeType: String? = null
)

data class SubscribeResponse(
    val subscriptionId: Long,
    val strategyId: Long,
    val message: String
)

data class UnsubscribeResponse(
    val strategyId: Long,
    val message: String
)

data class SubscriptionSummary(
    val subscriptionId: Long,
    val strategyId: Long,
    val strategyName: String,
    val strategyDescription: String?,
    val isPremiumStrategy: Boolean,
    val alertEnabled: Boolean,
    val preferredUniverseType: String,
    val subscribedAt: LocalDateTime
)

data class MySubscriptionsResponse(
    val subscriptions: List<SubscriptionSummary>,
    val total: Int
)

data class AlertUpdateRequest(
    val alertEnabled: Boolean
)

data class AlertUpdateResponse(
    val subscriptionId: Long,
    val alertEnabled: Boolean,
    val message: String
)
