package com.quantjumpstock.core.domain.model.tier

import java.time.LocalDateTime

data class TierConfiguration(
    val id: Long? = null,
    val tier: String,
    val maxSubscriptionCount: Int,
    val maxBacktestDaily: Int,
    val maxBacktestPerStrategy: Int,
    val isUnlimitedSubscription: Boolean,
    val isUnlimitedBacktest: Boolean,
    val description: String?,
    val updatedAt: LocalDateTime?,
    val updatedBy: String?
) {
    fun canSubscribeMore(currentCount: Long): Boolean {
        if (isUnlimitedSubscription) return true
        return currentCount < maxSubscriptionCount
    }

    fun canPerformBacktest(): Boolean = isUnlimitedBacktest
}
