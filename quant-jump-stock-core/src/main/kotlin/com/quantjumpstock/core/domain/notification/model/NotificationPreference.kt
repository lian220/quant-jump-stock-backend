package com.quantjumpstock.core.domain.notification.model

import java.time.LocalDateTime

data class NotificationPreference(
    val id: Long? = null,
    val userId: Long,
    val newsEnabled: Boolean = true,
    val announcementEnabled: Boolean = true,
    val tradingSignalEnabled: Boolean = true,
    val backtestEnabled: Boolean = true,
    val priceAlertEnabled: Boolean = true,
    val weeklyDigestEnabled: Boolean = true,
    val pushEnabled: Boolean = true,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
) {
    fun isTypeEnabled(type: NotificationType): Boolean = when (type) {
        NotificationType.NEWS -> newsEnabled
        NotificationType.ANNOUNCEMENT -> announcementEnabled
        NotificationType.TRADING_SIGNAL -> tradingSignalEnabled
        NotificationType.BACKTEST_COMPLETE -> backtestEnabled
        NotificationType.AI_ANALYSIS_COMPLETE -> backtestEnabled
        NotificationType.PRICE_ALERT -> priceAlertEnabled
        NotificationType.WEEKLY_DIGEST -> weeklyDigestEnabled
        NotificationType.STRATEGY_PERFORMANCE -> tradingSignalEnabled
        NotificationType.SUBSCRIPTION_EXPIRING -> true // 항상 발송
        NotificationType.USAGE_LIMIT_REACHED -> true   // 항상 발송
    }

    companion object {
        fun defaultFor(userId: Long) = NotificationPreference(userId = userId)
    }
}
