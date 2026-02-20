package com.quantjumpstock.core.application.admin

import com.quantjumpstock.core.domain.port.output.StrategySubscriptionRepository
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.domain.port.output.UserTierRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class AdminUserTierService(
    private val userRepository: UserRepository,
    private val userTierRepository: UserTierRepository,
    private val subscriptionRepository: StrategySubscriptionRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getUserTiersBatch(userDbIds: List<Long>): List<AdminUserTierInfo> {
        if (userDbIds.isEmpty()) return emptyList()

        val users = userRepository.findAllByDbIds(userDbIds).associateBy { it.id!! }
        val tiers = userTierRepository.getAdminTierDetailsBatch(userDbIds)
        val subscriptionCounts = subscriptionRepository.countActiveByUserIds(userDbIds)

        return userDbIds.mapNotNull { id ->
            val user = users[id] ?: return@mapNotNull null
            val tierDetails = tiers[id]
            AdminUserTierInfo(
                userId = id,
                userLoginId = user.userId,
                tier = tierDetails?.tier ?: "FREE",
                startedAt = tierDetails?.startedAt,
                expiresAt = tierDetails?.expiresAt,
                backtestCountToday = tierDetails?.backtestCountToday ?: 0,
                activeSubscriptionCount = subscriptionCounts[id] ?: 0
            )
        }
    }

    fun getUserTier(userDbId: Long): AdminUserTierInfo {
        val user = userRepository.findById(userDbId)
            ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다: $userDbId")

        val tierDetails = userTierRepository.getAdminTierDetails(userDbId)
        val activeSubscriptionCount = subscriptionRepository.countActiveByUserId(userDbId)

        return AdminUserTierInfo(
            userId = userDbId,
            userLoginId = user.userId,
            tier = tierDetails?.tier ?: "FREE",
            startedAt = tierDetails?.startedAt,
            expiresAt = tierDetails?.expiresAt,
            backtestCountToday = tierDetails?.backtestCountToday ?: 0,
            activeSubscriptionCount = activeSubscriptionCount
        )
    }

    @Transactional
    fun updateUserTier(userDbId: Long, request: UpdateUserTierRequest, updatedBy: String): AdminUserTierInfo {
        userRepository.findById(userDbId)
            ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다: $userDbId")

        val validTiers = listOf("FREE", "PREMIUM", "PREMIUM_YEARLY")
        val newTier = request.tier.uppercase()
        if (newTier !in validTiers) {
            throw IllegalArgumentException("유효하지 않은 티어입니다: ${request.tier}")
        }

        val startedAt = if (newTier == "FREE") null else LocalDateTime.now()
        val expiresAt = if (newTier == "FREE") null else request.expiresAt ?: startedAt?.plusYears(1)

        userTierRepository.updateAdminTier(userDbId, newTier, startedAt, expiresAt)
        logger.info("유저 티어 변경: userDbId={}, tier={}, updatedBy={}", userDbId, newTier, updatedBy)

        return getUserTier(userDbId)
    }
}

data class AdminUserTierInfo(
    val userId: Long,
    val userLoginId: String,
    val tier: String,
    val startedAt: LocalDateTime?,
    val expiresAt: LocalDateTime?,
    val backtestCountToday: Int,
    val activeSubscriptionCount: Long
)

data class UpdateUserTierRequest(
    val tier: String,
    val expiresAt: LocalDateTime? = null
)
