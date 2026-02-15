package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.quantjumpstock.core.domain.news.model.NewsNotification
import com.quantjumpstock.core.domain.news.model.NewsSubscription
import com.quantjumpstock.core.domain.news.port.output.NewsNotificationRepository
import com.quantjumpstock.core.domain.news.port.output.NewsSubscriptionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class NewsSubscriptionJpaAdapter(
    private val subscriptionRepository: UserNewsSubscriptionJpaRepository,
    private val notificationRepository: UserNewsNotificationJpaRepository
) : NewsSubscriptionRepository, NewsNotificationRepository {

    // === NewsSubscriptionRepository ===

    override fun findByUserAndTypeAndValueAndChannel(
        userId: Long, type: String, value: String, channel: String
    ): NewsSubscription? {
        return subscriptionRepository
            .findByUserIdAndSubscriptionTypeAndSubscriptionValueAndNotifyChannel(userId, type, value, channel)
            ?.toDomain()
    }

    override fun countActiveByUser(userId: Long): Long {
        return subscriptionRepository.countByUserIdAndIsActiveTrue(userId)
    }

    override fun save(subscription: NewsSubscription): NewsSubscription {
        val entity = if (subscription.id != null) {
            val existing = subscriptionRepository.findById(subscription.id).orElse(null)
            if (existing != null) {
                existing.isActive = subscription.isActive
                existing
            } else {
                subscription.toEntity()
            }
        } else {
            subscription.toEntity()
        }
        return subscriptionRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): NewsSubscription? {
        return subscriptionRepository.findById(id).orElse(null)?.toDomain()
    }

    override fun findActiveByUser(userId: Long): List<NewsSubscription> {
        return subscriptionRepository.findByUserIdAndIsActiveTrue(userId).map { it.toDomain() }
    }

    override fun findActiveByTypeAndValue(type: String, value: String): List<NewsSubscription> {
        return subscriptionRepository
            .findBySubscriptionTypeAndSubscriptionValueAndIsActiveTrue(type, value)
            .map { it.toDomain() }
    }

    // === NewsNotificationRepository ===

    override fun save(notification: NewsNotification): NewsNotification {
        return notificationRepository.save(notification.toEntity()).toDomain()
    }

    @Transactional
    override fun saveAll(notifications: List<NewsNotification>): List<NewsNotification> {
        if (notifications.isEmpty()) return emptyList()
        return notificationRepository.saveAll(notifications.map { it.toEntity() }).map { it.toDomain() }
    }

    override fun findByUserPaged(userId: Long, limit: Int): List<NewsNotification> {
        return notificationRepository
            .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
            .map { it.toDomain() }
    }

    override fun countUnreadByUser(userId: Long): Long {
        return notificationRepository.countByUserIdAndIsReadFalse(userId)
    }

    override fun markAsRead(id: Long, userId: Long): Int {
        return notificationRepository.markAsRead(id, userId)
    }

    override fun markAllAsRead(userId: Long): Int {
        return notificationRepository.markAllAsRead(userId)
    }

    // === Mapping ===

    private fun UserNewsSubscriptionEntity.toDomain() = NewsSubscription(
        id = id,
        userId = userId,
        subscriptionType = subscriptionType,
        subscriptionValue = subscriptionValue,
        displayName = displayName,
        notifyChannel = notifyChannel,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun NewsSubscription.toEntity() = UserNewsSubscriptionEntity(
        id = id,
        userId = userId,
        subscriptionType = subscriptionType,
        subscriptionValue = subscriptionValue,
        displayName = displayName,
        notifyChannel = notifyChannel,
        isActive = isActive
    )

    private fun UserNewsNotificationEntity.toDomain() = NewsNotification(
        id = id,
        userId = userId,
        newsId = newsId,
        categoryName = categoryName,
        title = title,
        message = message,
        importance = importance,
        sourceUrl = sourceUrl,
        isRead = isRead,
        createdAt = createdAt
    )

    private fun NewsNotification.toEntity() = UserNewsNotificationEntity(
        id = id,
        userId = userId,
        newsId = newsId,
        categoryName = categoryName,
        title = title,
        message = message,
        importance = importance,
        sourceUrl = sourceUrl
    )
}
