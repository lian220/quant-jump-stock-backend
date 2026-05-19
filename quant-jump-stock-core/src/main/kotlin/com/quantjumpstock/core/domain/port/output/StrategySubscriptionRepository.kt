package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.backtest.UniverseType

/**
 * 구독 유니버스 필터링에 필요한 최소 뷰 (SCRUM-349)
 */
data class StrategySubscriptionView(
    val strategyId: Long,
    val preferredUniverseType: UniverseType,
    val brokerAccountId: Long? = null,
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
    val subscribedAt: java.time.LocalDateTime,
    val brokerAccountId: Long? = null,
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

    /** 여러 유저의 활성 구독 수 배치 조회 */
    fun countActiveByUserIds(userDbIds: List<Long>): Map<Long, Long>

    /** 구독 상세 목록 조회 */
    fun findDetailsByUserId(userDbId: Long): List<SubscriptionDetail>

    /** 특정 구독 단건 조회 */
    fun findByIdAndUserId(subscriptionId: Long, userDbId: Long): SubscriptionDetail?

    /** 알림 설정 변경 */
    fun updateAlertEnabled(subscriptionId: Long, alertEnabled: Boolean): Boolean

    /**
     * 구독 → 계좌 매핑 변경 (Phase 1B v2.1).
     * `brokerAccountId = null` 이면 legacy fallback (사용자 활성 계좌 자동 선택).
     */
    fun updateBrokerAccountId(subscriptionId: Long, brokerAccountId: Long?): Boolean

    /**
     * Phase 1C — 계좌-구독 1:1 제약 검사.
     * 같은 broker_account_id 를 가진 다른 ACTIVE 구독을 lookup.
     * @param excludeSubscriptionId 현재 PATCH 대상 구독 (자기 자신은 conflict 아님). null 이면 제외 없음.
     * @return 충돌 구독 (없으면 null). 1:1 제약으로 결과는 최대 1건.
     */
    fun findActiveSubscriptionByBrokerAccountId(
        brokerAccountId: Long,
        excludeSubscriptionId: Long?,
    ): SubscriptionDetail?

    /** 이미 구독 중인지 확인 */
    fun existsActiveByUserIdAndStrategyId(userDbId: Long, strategyId: Long): Boolean

    /** 특정 전략에 대한 활성 구독 ID 조회 (없으면 null) */
    fun findActiveSubscriptionId(userDbId: Long, strategyId: Long): Long?

    /** 어드민용: 페이징 기반 구독 현황 조회 */
    fun findAdminSubscriptions(strategyId: Long?, userId: Long?, page: Int, size: Int): AdminSubscriptionPage
}

data class AdminSubscriptionInfo(
    val subscriptionId: Long,
    val userDbId: Long,
    val userLoginId: String,
    val strategyId: Long,
    val strategyName: String,
    val status: String,
    val alertEnabled: Boolean,
    val subscribedAt: java.time.LocalDateTime
)

data class AdminSubscriptionPage(
    val items: List<AdminSubscriptionInfo>,
    val total: Long,
    val activeCount: Long
)

data class SubscribeResult(
    val success: Boolean,
    val subscriptionId: Long?,
    val alreadySubscribed: Boolean
)
