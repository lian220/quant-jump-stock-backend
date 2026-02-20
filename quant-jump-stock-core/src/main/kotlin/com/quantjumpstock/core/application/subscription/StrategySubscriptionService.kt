package com.quantjumpstock.core.application.subscription

import com.quantjumpstock.core.application.tier.TierConfigurationService
import com.quantjumpstock.core.domain.model.backtest.UniverseType
import com.quantjumpstock.core.domain.model.strategy.StrategyStatus
import com.quantjumpstock.core.domain.port.output.StrategyRepository
import com.quantjumpstock.core.domain.port.output.StrategySubscriptionRepository
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.domain.port.output.UserTierRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class StrategySubscriptionService(
    private val subscriptionRepository: StrategySubscriptionRepository,
    private val userRepository: UserRepository,
    private val strategyRepository: StrategyRepository,
    private val tierConfigService: TierConfigurationService,
    private val userTierRepository: UserTierRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun subscribe(userId: String, strategyId: Long, universeTypeName: String?): SubscribeResponse {
        // 1) 유저 조회
        val user = userRepository.findByUserId(userId)
            ?: throw SubscriptionException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", 404)

        val userDbId = user.id ?: throw SubscriptionException("USER_NOT_FOUND", "사용자 ID를 찾을 수 없습니다.", 404)

        // 2) 전략 존재/공개 확인
        val strategy = strategyRepository.findById(strategyId)
            ?: throw SubscriptionException("STRATEGY_NOT_FOUND", "전략을 찾을 수 없습니다: $strategyId", 404)

        val allowedStatuses = listOf(StrategyStatus.PUBLISHED, StrategyStatus.ACTIVE)
        if (strategy.status !in allowedStatuses || !strategy.isPublic) {
            throw SubscriptionException("STRATEGY_NOT_AVAILABLE", "구독할 수 없는 전략입니다.", 404)
        }

        // 3) 프리미엄 전략 접근 권한 체크
        val tierInfo = userTierRepository.checkBacktestLimit(userId)
        if (strategy.isPremium && tierInfo.tier == "FREE") {
            throw SubscriptionException(
                "PREMIUM_REQUIRED",
                "프리미엄 전략은 프리미엄 구독이 필요합니다.",
                402
            )
        }

        // 4) 중복 구독 체크
        if (subscriptionRepository.existsActiveByUserIdAndStrategyId(userDbId, strategyId)) {
            throw SubscriptionException("ALREADY_SUBSCRIBED", "이미 구독 중인 전략입니다.", 400)
        }

        // 5) 구독 제한 체크 (tierConfig에서 조회)
        val currentCount = subscriptionRepository.countActiveByUserId(userDbId)
        val limitResult = tierConfigService.checkSubscriptionLimit(tierInfo.tier, currentCount)
        if (!limitResult.allowed) {
            throw SubscriptionException(
                "SUBSCRIPTION_LIMIT_EXCEEDED",
                limitResult.message ?: "구독 제한을 초과했습니다.",
                403,
                mapOf("currentCount" to currentCount, "maxCount" to limitResult.maxCount)
            )
        }

        // 6) 구독 생성
        val universeType = universeTypeName?.let {
            try { UniverseType.valueOf(it) } catch (_: Exception) { strategy.recommendedUniverseType }
        } ?: strategy.recommendedUniverseType

        val result = subscriptionRepository.subscribe(userDbId, strategyId, universeType)
        if (!result.success) {
            throw SubscriptionException("ALREADY_SUBSCRIBED", "이미 구독 중인 전략입니다.", 400)
        }

        logger.info("전략 구독 완료: userId={}, strategyId={}, subscriptionId={}", userId, strategyId, result.subscriptionId)

        return SubscribeResponse(
            subscriptionId = result.subscriptionId
                ?: throw SubscriptionException("INTERNAL_ERROR", "구독 ID 생성 오류", 500),
            strategyId = strategyId,
            message = "구독이 완료되었습니다."
        )
    }

    @Transactional
    fun unsubscribe(userId: String, strategyId: Long): UnsubscribeResponse {
        val user = userRepository.findByUserId(userId)
            ?: throw SubscriptionException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", 404)

        val userDbId = user.id ?: throw SubscriptionException("USER_NOT_FOUND", "사용자 ID를 찾을 수 없습니다.", 404)

        val cancelled = subscriptionRepository.unsubscribe(userDbId, strategyId)
        if (!cancelled) {
            throw SubscriptionException("SUBSCRIPTION_NOT_FOUND", "구독을 찾을 수 없습니다.", 404)
        }

        logger.info("전략 구독 취소: userId={}, strategyId={}", userId, strategyId)

        return UnsubscribeResponse(
            strategyId = strategyId,
            message = "구독이 취소되었습니다."
        )
    }

    fun getMySubscriptions(userId: String): MySubscriptionsResponse {
        val user = userRepository.findByUserId(userId)
            ?: throw SubscriptionException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", 404)

        val userDbId = user.id ?: throw SubscriptionException("USER_NOT_FOUND", "사용자 ID를 찾을 수 없습니다.", 404)

        val details = subscriptionRepository.findDetailsByUserId(userDbId)
        val summaries = details.map { d ->
            SubscriptionSummary(
                subscriptionId = d.subscriptionId,
                strategyId = d.strategyId,
                strategyName = d.strategyName,
                strategyDescription = d.strategyDescription,
                isPremiumStrategy = d.isPremiumStrategy,
                alertEnabled = d.alertEnabled,
                preferredUniverseType = d.preferredUniverseType.name,
                subscribedAt = d.subscribedAt
            )
        }

        return MySubscriptionsResponse(subscriptions = summaries, total = summaries.size)
    }

    @Transactional
    fun updateAlert(userId: String, subscriptionId: Long, alertEnabled: Boolean): AlertUpdateResponse {
        val user = userRepository.findByUserId(userId)
            ?: throw SubscriptionException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", 404)

        val userDbId = user.id ?: throw SubscriptionException("USER_NOT_FOUND", "사용자 ID를 찾을 수 없습니다.", 404)

        // 소유권 확인
        subscriptionRepository.findByIdAndUserId(subscriptionId, userDbId)
            ?: throw SubscriptionException("SUBSCRIPTION_NOT_FOUND", "구독을 찾을 수 없습니다.", 404)

        subscriptionRepository.updateAlertEnabled(subscriptionId, alertEnabled)

        return AlertUpdateResponse(
            subscriptionId = subscriptionId,
            alertEnabled = alertEnabled,
            message = if (alertEnabled) "알림이 활성화되었습니다." else "알림이 비활성화되었습니다."
        )
    }
}

class SubscriptionException(
    val errorCode: String,
    message: String,
    val httpStatus: Int,
    val extra: Map<String, Any> = emptyMap()
) : RuntimeException(message)
