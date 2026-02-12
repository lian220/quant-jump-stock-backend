package com.quantjumpstock.core.application.backtest

import com.quantjumpstock.core.domain.port.output.UserTierRepository
import org.springframework.stereotype.Service

@Service
class UserTierService(
    private val userTierRepository: UserTierRepository
) {

    fun checkBacktestLimit(userId: String): BacktestLimitResult {
        if (userId == UNLIMITED_USER) {
            return BacktestLimitResult(
                allowed = true,
                remaining = Int.MAX_VALUE,
                dailyLimit = Int.MAX_VALUE,
                tier = "ADMIN",
                message = null
            )
        }
        val info = userTierRepository.checkBacktestLimit(userId)
        return BacktestLimitResult(
            allowed = info.allowed,
            remaining = info.remaining,
            dailyLimit = info.dailyLimit,
            tier = info.tier,
            message = info.message
        )
    }

    fun incrementBacktestCount(userId: String) {
        userTierRepository.incrementBacktestCount(userId)
    }

    fun checkAndIncrementBacktestCount(userId: String): BacktestLimitResult {
        if (userId == UNLIMITED_USER) {
            return BacktestLimitResult(
                allowed = true,
                remaining = Int.MAX_VALUE,
                dailyLimit = Int.MAX_VALUE,
                tier = "ADMIN",
                message = null
            )
        }
        val info = userTierRepository.checkAndIncrementBacktestCount(userId)
        return BacktestLimitResult(
            allowed = info.allowed,
            remaining = info.remaining,
            dailyLimit = info.dailyLimit,
            tier = info.tier,
            message = info.message
        )
    }

    companion object {
        private const val UNLIMITED_USER = "n_user_74cc63b0"
    }
}
