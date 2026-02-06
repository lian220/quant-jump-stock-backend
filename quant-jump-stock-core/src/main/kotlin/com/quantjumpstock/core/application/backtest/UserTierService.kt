package com.quantjumpstock.core.application.backtest

import com.quantjumpstock.core.domain.port.output.UserTierRepository
import org.springframework.stereotype.Service

@Service
class UserTierService(
    private val userTierRepository: UserTierRepository
) {

    fun checkBacktestLimit(userId: String): BacktestLimitResult {
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
}
