package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserTierEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserTierJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserTier
import com.quantjumpstock.core.domain.port.output.BacktestLimitInfo
import com.quantjumpstock.core.domain.port.output.UserTierRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserTierPersistenceAdapter(
    private val userTierJpaRepository: UserTierJpaRepository,
    private val userJpaRepository: UserJpaRepository
) : UserTierRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

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
    override fun createFreeTierForUser(userId: String) {
        val userEntity = userJpaRepository.findByUserId(userId).orElse(null) ?: run {
            logger.warn("무료 티어 생성 실패 - 사용자 없음: userId=$userId")
            return
        }

        val userPk = userEntity.id!!
        if (userTierJpaRepository.existsByUserId(userPk)) {
            logger.debug("이미 티어가 존재합니다: userId=$userId")
            return
        }

        userTierJpaRepository.save(UserTierEntity(user = userEntity))
        logger.info("사용자 무료 티어 생성 완료: userId=$userId, tier=FREE")
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

    @Transactional
    override fun checkAndIncrementBacktestCount(userId: String): BacktestLimitInfo {
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

        // 티어 엔티티 없으면 생성 (첫 백테스트)
        if (tierEntity == null) {
            val newTier = userTierJpaRepository.save(UserTierEntity(user = userEntity))
            newTier.incrementBacktestCount()
            userTierJpaRepository.save(newTier)
            return BacktestLimitInfo(
                allowed = true,
                remaining = UserTierEntity.FREE_DAILY_LIMIT - 1,
                dailyLimit = UserTierEntity.FREE_DAILY_LIMIT,
                tier = UserTier.FREE.name
            )
        }

        // PREMIUM 유저는 무제한
        if (tierEntity.tier == UserTier.PREMIUM || tierEntity.tier == UserTier.PREMIUM_YEARLY) {
            tierEntity.incrementBacktestCount()
            userTierJpaRepository.save(tierEntity)
            return BacktestLimitInfo(
                allowed = true,
                remaining = -1,
                dailyLimit = -1,
                tier = tierEntity.tier.name
            )
        }

        // FREE 유저: 원자적 조건부 증가
        val limit = UserTierEntity.FREE_DAILY_LIMIT
        val updated = userTierJpaRepository.incrementBacktestCountIfBelowLimit(userPk, limit)

        return if (updated > 0) {
            // 증가 성공 → 남은 횟수 조회
            val refreshed = userTierJpaRepository.findByUserId(userPk).orElse(tierEntity)
            BacktestLimitInfo(
                allowed = true,
                remaining = refreshed.getRemainingBacktests(),
                dailyLimit = limit,
                tier = tierEntity.tier.name
            )
        } else {
            // 한도 초과
            BacktestLimitInfo(
                allowed = false,
                remaining = 0,
                dailyLimit = limit,
                tier = tierEntity.tier.name,
                message = "일일 백테스트 한도를 초과했습니다. (${limit}회/일)"
            )
        }
    }
}
