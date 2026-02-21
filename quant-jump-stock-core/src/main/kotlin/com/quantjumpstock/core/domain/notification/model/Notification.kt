package com.quantjumpstock.core.domain.notification.model

import java.time.LocalDateTime

data class Notification(
    val id: Long? = null,
    val userId: Long,
    val type: NotificationType,
    val priority: NotificationPriority = NotificationPriority.NORMAL,
    val title: String,
    val message: String? = null,
    val actionUrl: String? = null,
    val metadata: Map<String, Any>? = null,
    val isRead: Boolean = false,
    val createdAt: LocalDateTime? = null
)

enum class NotificationType {
    BACKTEST_COMPLETE, AI_ANALYSIS_COMPLETE, NEWS, TRADING_SIGNAL,
    ANNOUNCEMENT, PRICE_ALERT, SUBSCRIPTION_EXPIRING,
    USAGE_LIMIT_REACHED, STRATEGY_PERFORMANCE, WEEKLY_DIGEST
}

enum class NotificationPriority { CRITICAL, HIGH, NORMAL, LOW }
