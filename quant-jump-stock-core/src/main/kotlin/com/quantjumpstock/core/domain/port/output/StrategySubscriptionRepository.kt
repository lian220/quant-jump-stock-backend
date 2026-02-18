package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.backtest.UniverseType

/**
 * 구독 유니버스 필터링에 필요한 최소 뷰 (SCRUM-349)
 */
data class StrategySubscriptionView(
    val strategyId: Long,
    val preferredUniverseType: UniverseType
)

interface StrategySubscriptionRepository {

    /** 사용자의 활성(ACTIVE) 구독 목록 조회 */
    fun findActiveByUserId(userId: Long): List<StrategySubscriptionView>
}
