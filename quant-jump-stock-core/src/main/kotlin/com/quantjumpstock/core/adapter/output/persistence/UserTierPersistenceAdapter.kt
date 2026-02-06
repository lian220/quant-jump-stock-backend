package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserTierEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserTierJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserTier
import com.quantjumpstock.core.domain.port.output.BacktestLimitInfo
import com.quantjumpstock.core.domain.port.output.UserTierRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserTierPersistenceAdapter(
    private val userTierJpaRepository: UserTierJpaRepository,
    private val userJpaRepository: UserJpaRepository
) : UserTierRepository {

    @Transactional(readOnly = true)
    override fun checkBacktestLimit(userId: String): BacktestLimitInfo {
        val userEntity = userJpaRepository.findByUserId(userId).orElse(null)
            ?: return BacktestLimitInfo(
                allowed = false,
                remaining = 0,
                dailyLimit = UserTierEntity.FREE_DAILY_LIMIT,
                tier = UserTier.FREE.name,
                message = "사용자를 찾을 수 없습니다."
            )

        val userPk = userEntity.id!!
        val tierEntity = userTierJpaRepository.findByUserId(userPk).orElse(null)

        if (tierEntity == null) {
            return BacktestLimitInfo(
                allowed = true,
                remaining = UserTierEntity.FREE_DAILY_LIMIT,
                dailyLimit = UserTierEntity.FREE_DAILY_LIMIT,
                tier = UserTier.FREE.name
            )
        }

        val allowed = tierEntity.canPerformBacktest()
        val remaining = tierEntity.getRemainingBacktests()
        val dailyLimit = when (tierEntity.tier) {
            UserTier.FREE -> UserTierEntity.FREE_DAILY_LIMIT
            UserTier.PREMIUM, UserTier.PREMIUM_YEARLY -> -1
        }

        return BacktestLimitInfo(
            allowed = allowed,
            remaining = remaining,
            dailyLimit = dailyLimit,
            tier = tierEntity.tier.name,
            message = if (!allowed) "일일 백테스트 한도를 초과했습니다. (${UserTierEntity.FREE_DAILY_LIMIT}회/일)" else null
        )
    }

    @Transactional
    override fun incrementBacktestCount(userId: String) {
        val userEntity = userJpaRepository.findByUserId(userId).orElseThrow {
            IllegalArgumentException("사용자를 찾을 수 없습니다: $userId")
        }

        val userPk = userEntity.id!!
        val tierEntity = userTierJpaRepository.findByUserId(userPk).orElseGet {
            userTierJpaRepository.save(UserTierEntity(user = userEntity))
        }

        tierEntity.incrementBacktestCount()
        userTierJpaRepository.save(tierEntity)
    }
}
