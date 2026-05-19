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
    val subscribedAt: LocalDateTime,
    val brokerAccountId: Long? = null,
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

/**
 * 전략 구독의 실행 계좌 변경 요청 (Phase 1B v2.1).
 * `brokerAccountId = null` 이면 legacy fallback (사용자 활성 계좌 자동 선택).
 */
data class BrokerAccountUpdateRequest(
    val brokerAccountId: Long?,
)

data class BrokerAccountUpdateResponse(
    val subscriptionId: Long,
    val brokerAccountId: Long?,
    val message: String,
)
