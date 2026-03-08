package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.fasterxml.jackson.databind.ObjectMapper
import com.quantjumpstock.core.domain.notification.model.Notification
import com.quantjumpstock.core.domain.notification.port.output.NotificationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class NotificationJpaAdapter(
    private val jpaRepository: NotificationJpaRepository,
    private val objectMapper: ObjectMapper
) : NotificationRepository {

    override fun save(notification: Notification): Notification =
        jpaRepository.save(notification.toEntity()).toDomain()

    @Transactional
    override fun saveAll(notifications: List<Notification>): List<Notification> =
        jpaRepository.saveAll(notifications.map { it.toEntity() }).map { it.toDomain() }

    override fun findByUserPaged(userId: Long, limit: Int): List<Notification> =
        jpaRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
            .map { it.toDomain() }

    override fun countUnreadByUser(userId: Long): Long =
        jpaRepository.countByUserIdAndIsReadFalse(userId)

    override fun countByUserIdSince(userId: Long, since: java.time.LocalDateTime): Long =
        jpaRepository.countByUserIdAndCreatedAtAfter(userId, since)

    @Transactional
    override fun markAsRead(id: Long, userId: Long): Int =
        jpaRepository.markAsRead(id, userId)

    @Transactional
    override fun markAllAsRead(userId: Long): Int =
        jpaRepository.markAllAsRead(userId)

    // === 매핑 함수 ===

    @Suppress("UNCHECKED_CAST")
    private fun NotificationEntity.toDomain() = Notification(
        id = id,
        userId = userId,
        type = type,
        priority = priority,
        title = title,
        message = message,
        actionUrl = actionUrl,
        metadata = metadata?.let { objectMapper.readValue(it, Map::class.java) as? Map<String, Any> },
        isRead = isRead,
        createdAt = createdAt
    )

    private fun Notification.toEntity() = NotificationEntity(
        id = id,
        userId = userId,
        type = type,
        priority = priority,
        title = title,
        message = message,
        actionUrl = actionUrl,
        metadata = metadata?.let { objectMapper.writeValueAsString(it) },
        isRead = isRead,
        createdAt = createdAt
    )
}
