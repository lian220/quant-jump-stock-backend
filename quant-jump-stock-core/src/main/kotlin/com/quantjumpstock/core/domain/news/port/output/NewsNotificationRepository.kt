package com.quantjumpstock.core.domain.news.port.output

import com.quantjumpstock.core.domain.news.model.NewsNotification

interface NewsNotificationRepository {
    fun save(notification: NewsNotification): NewsNotification
    fun saveAll(notifications: List<NewsNotification>): List<NewsNotification>
    fun findByUserPaged(userId: Long, limit: Int): List<NewsNotification>
    fun countUnreadByUser(userId: Long): Long
    fun markAsRead(id: Long, userId: Long): Int
    fun markAllAsRead(userId: Long): Int
}
