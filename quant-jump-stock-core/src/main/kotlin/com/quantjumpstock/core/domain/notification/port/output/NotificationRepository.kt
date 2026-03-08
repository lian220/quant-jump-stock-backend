package com.quantjumpstock.core.domain.notification.port.output

import com.quantjumpstock.core.domain.notification.model.Notification

interface NotificationRepository {
    fun save(notification: Notification): Notification
    fun saveAll(notifications: List<Notification>): List<Notification>
    fun findByUserPaged(userId: Long, limit: Int): List<Notification>
    fun countUnreadByUser(userId: Long): Long
    fun countByUserIdSince(userId: Long, since: java.time.LocalDateTime): Long
    fun markAsRead(id: Long, userId: Long): Int
    fun markAllAsRead(userId: Long): Int
}
