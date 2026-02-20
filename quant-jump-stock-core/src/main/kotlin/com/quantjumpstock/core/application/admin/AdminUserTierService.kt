package com.quantjumpstock.core.application.admin

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserTier
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserTierEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserTierJpaRepository
import com.quantjumpstock.core.domain.port.output.StrategySubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class AdminUserTierService(
    private val userJpaRepository: UserJpaRepository,
    private val userTierJpaRepository: UserTierJpaRepository,
    private val subscriptionRepository: StrategySubscriptionRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getUserTiersBatch(userDbIds: List<Long>): List<AdminUserTierInfo> {
        if (userDbIds.isEmpty()) return emptyList()

        val users = userJpaRepository.findAllById(userDbIds).associateBy { it.id!! }
        val tiers = userTierJpaRepository.findByUserIdIn(userDbIds).associateBy { it.user.id!! }
        val subscriptionCounts = subscriptionRepository.countActiveByUserIds(userDbIds)

        return userDbIds.mapNotNull { id ->
            val userEntity = users[id] ?: return@mapNotNull null
            val tierEntity = tiers[id]
            AdminUserTierInfo(
                userId = id,
                userLoginId = userEntity.userId,
                tier = tierEntity?.tier?.name ?: "FREE",
                startedAt = tierEntity?.startedAt,
                expiresAt = tierEntity?.expiresAt,
                backtestCountToday = tierEntity?.backtestCountToday ?: 0,
                activeSubscriptionCount = subscriptionCounts[id] ?: 0
            )
        }
    }

    fun getUserTier(userDbId: Long): AdminUserTierInfo {
        val userEntity = userJpaRepository.findById(userDbId).orElseThrow {
            IllegalArgumentException("사용자를 찾을 수 없습니다: $userDbId")
        }

        val tierEntity = userTierJpaRepository.findByUserId(userDbId).orElse(null)
        val activeSubscriptionCount = subscriptionRepository.countActiveByUserId(userDbId)

        return AdminUserTierInfo(
            userId = userDbId,
            userLoginId = userEntity.userId,
            tier = tierEntity?.tier?.name ?: "FREE",
            startedAt = tierEntity?.startedAt,
            expiresAt = tierEntity?.expiresAt,
            backtestCountToday = tierEntity?.backtestCountToday ?: 0,
            activeSubscriptionCount = activeSubscriptionCount
        )
    }

    @Transactional
    fun updateUserTier(userDbId: Long, request: UpdateUserTierRequest, updatedBy: String): AdminUserTierInfo {
        val userEntity = userJpaRepository.findById(userDbId).orElseThrow {
            IllegalArgumentException("사용자를 찾을 수 없습니다: $userDbId")
        }

        val newTier = try {
            UserTier.valueOf(request.tier.uppercase())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("유효하지 않은 티어입니다: ${request.tier}")
        }

        val tierEntity = userTierJpaRepository.findByUserId(userDbId).orElse(null)

        if (newTier == UserTier.FREE) {
            if (tierEntity != null) {
                tierEntity.tier = UserTier.FREE
                tierEntity.expiresAt = null
            }
            logger.info("유저 티어 다운그레이드: userId={}, tier=FREE, updatedBy={}", userDbId, updatedBy)
        } else {
            val startedAt = LocalDateTime.now()
            val expiresAt = request.expiresAt ?: startedAt.plusYears(1)
            if (tierEntity != null) {
                tierEntity.tier = newTier
                tierEntity.startedAt = startedAt
                tierEntity.expiresAt = expiresAt
            } else {
                val newTierEntity = UserTierEntity(
                    user = userEntity,
                    tier = newTier,
                    startedAt = startedAt,
                    expiresAt = expiresAt
                )
                userTierJpaRepository.save(newTierEntity)
            }
            logger.info("유저 티어 업그레이드: userId={}, tier={}, expiresAt={}, updatedBy={}", userDbId, newTier, expiresAt, updatedBy)
        }

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
