package com.quantjumpstock.core.application.notification

import com.quantjumpstock.core.domain.notification.model.Notification
import com.quantjumpstock.core.domain.notification.model.NotificationPriority
import com.quantjumpstock.core.domain.notification.model.NotificationType
import com.quantjumpstock.core.domain.notification.port.output.NotificationPreferenceRepository
import com.quantjumpstock.core.domain.notification.port.output.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val preferenceRepository: NotificationPreferenceRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun create(
        userId: Long,
        type: NotificationType,
        priority: NotificationPriority = NotificationPriority.NORMAL,
        title: String,
        message: String? = null,
        actionUrl: String? = null,
        metadata: Map<String, Any>? = null
    ): Notification? {
        val preference = preferenceRepository.findByUserId(userId)
        if (preference != null && !preference.isTypeEnabled(type)) {
            logger.debug("알림 스킵 (사용자 설정 off): userId={}, type={}", userId, type)
            return null
        }

        val notification = Notification(
            userId = userId,
            type = type,
            priority = priority,
            title = title,
            message = message,
            actionUrl = actionUrl,
            metadata = metadata
        )
        val saved = notificationRepository.save(notification)
        logger.info("알림 생성: userId={}, type={}, title={}", userId, type, title.take(30))
        return saved
    }

    @Transactional
    fun createBatch(notifications: List<Notification>): List<Notification> {
        if (notifications.isEmpty()) return emptyList()

        val userIds = notifications.map { it.userId }.distinct()
        val preferenceMap = preferenceRepository.findByUserIds(userIds).associateBy { it.userId }

        val filtered = notifications.filter { notification ->
            val preference = preferenceMap[notification.userId]
            preference == null || preference.isTypeEnabled(notification.type)
        }

        if (filtered.isEmpty()) return emptyList()
        val saved = notificationRepository.saveAll(filtered)
        logger.info("알림 배치 생성: {}건 (필터 전 {}건)", saved.size, notifications.size)
        return saved
    }

    fun getUserNotifications(userId: Long, limit: Int = 20): List<Notification> =
        notificationRepository.findByUserPaged(userId, limit)

    fun getUnreadCount(userId: Long): Long =
        notificationRepository.countUnreadByUser(userId)

    fun countTodayNotifications(userId: Long): Long {
        val todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN)
        return notificationRepository.countByUserIdSince(userId, todayStart)
    }

    @Transactional
    fun markAsRead(userId: Long, notificationId: Long): Boolean =
        notificationRepository.markAsRead(notificationId, userId) > 0

    @Transactional
    fun markAllAsRead(userId: Long): Int =
        notificationRepository.markAllAsRead(userId)
}
