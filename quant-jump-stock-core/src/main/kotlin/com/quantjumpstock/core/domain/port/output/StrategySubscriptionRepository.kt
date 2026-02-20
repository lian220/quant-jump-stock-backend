package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.backtest.UniverseType

/**
 * 구독 유니버스 필터링에 필요한 최소 뷰 (SCRUM-349)
 */
data class StrategySubscriptionView(
    val strategyId: Long,
    val preferredUniverseType: UniverseType
)

/**
 * 구독 상세 뷰 (SCRUM-206, 207)
 */
data class SubscriptionDetail(
    val subscriptionId: Long,
    val strategyId: Long,
    val strategyName: String,
    val strategyDescription: String?,
    val isPremiumStrategy: Boolean,
    val alertEnabled: Boolean,
    val preferredUniverseType: UniverseType,
    val subscribedAt: java.time.LocalDateTime
)

interface StrategySubscriptionRepository {

    /** 사용자의 활성(ACTIVE) 구독 목록 조회 */
    fun findActiveByUserId(userId: Long): List<StrategySubscriptionView>

    /** 구독 생성 (이미 ACTIVE면 false 반환) */
    fun subscribe(userDbId: Long, strategyId: Long, universeType: UniverseType): SubscribeResult

    /** 구독 취소 */
    fun unsubscribe(userDbId: Long, strategyId: Long): Boolean

    /** 활성 구독 수 조회 */
    fun countActiveByUserId(userDbId: Long): Long

    /** 구독 상세 목록 조회 */
    fun findDetailsByUserId(userDbId: Long): List<SubscriptionDetail>

    /** 특정 구독 단건 조회 */
    fun findByIdAndUserId(subscriptionId: Long, userDbId: Long): SubscriptionDetail?

    /** 알림 설정 변경 */
    fun updateAlertEnabled(subscriptionId: Long, alertEnabled: Boolean): Boolean

    /** 이미 구독 중인지 확인 */
    fun existsActiveByUserIdAndStrategyId(userDbId: Long, strategyId: Long): Boolean

    /** 특정 전략에 대한 활성 구독 ID 조회 (없으면 null) */
    fun findActiveSubscriptionId(userDbId: Long, strategyId: Long): Long?
}

data class SubscribeResult(
    val success: Boolean,
    val subscriptionId: Long?,
    val alreadySubscribed: Boolean
)
