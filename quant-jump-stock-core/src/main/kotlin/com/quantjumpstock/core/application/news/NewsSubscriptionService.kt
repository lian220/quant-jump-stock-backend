package com.quantjumpstock.core.application.news

import com.quantjumpstock.core.domain.news.model.NewsNotification
import com.quantjumpstock.core.domain.news.model.NewsSubscription
import com.quantjumpstock.core.domain.news.port.output.NewsNotificationRepository
import com.quantjumpstock.core.domain.news.port.output.NewsSubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter

@Service
class NewsSubscriptionService(
    private val subscriptionRepository: NewsSubscriptionRepository,
    private val notificationRepository: NewsNotificationRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    // === 구독 관리 ===

    @Transactional
    fun subscribe(userId: Long, request: SubscribeRequest): SubscriptionResponse {
        val type = request.type.uppercase()
        val channel = request.channel.uppercase()

        // 중복 체크
        val existing = subscriptionRepository.findByUserAndTypeAndValueAndChannel(
            userId, type, request.value, channel
        )
        if (existing != null) {
            // 비활성이면 재활성화
            if (!existing.isActive) {
                val reactivated = existing.copy(isActive = true)
                subscriptionRepository.save(reactivated)
                return reactivated.toResponse()
            }
            return existing.toResponse()
        }

        // 구독 상한 체크 (사용자당 최대 50개)
        val count = subscriptionRepository.countActiveByUser(userId)
        if (count >= MAX_SUBSCRIPTIONS) {
            throw IllegalStateException("최대 구독 수(${MAX_SUBSCRIPTIONS}개)를 초과했습니다.")
        }

        val displayName = buildDisplayName(type, request.value, channel)
        val subscription = NewsSubscription(
            userId = userId,
            subscriptionType = type,
            subscriptionValue = request.value,
            displayName = displayName,
            notifyChannel = channel
        )
        val saved = subscriptionRepository.save(subscription)
        logger.info("사용자 {} 구독 추가: {} - {} ({})", userId, type, request.value, channel)
        return saved.toResponse()
    }

    @Transactional
    fun unsubscribe(userId: Long, subscriptionId: Long): Boolean {
        val subscription = subscriptionRepository.findById(subscriptionId) ?: return false
        if (subscription.userId != userId) return false
        subscriptionRepository.save(subscription.copy(isActive = false))
        logger.info("사용자 {} 구독 해제: {}", userId, subscriptionId)
        return true
    }

    fun getUserSubscriptions(userId: Long): SubscriptionListResponse {
        val subs = subscriptionRepository.findActiveByUser(userId)
        return SubscriptionListResponse(
            subscriptions = subs.map { it.toResponse() },
            total = subs.size
        )
    }

    // === 알림 관리 ===

    fun getUserNotifications(userId: Long, limit: Int = 30): NotificationListResponse {
        val notifications = notificationRepository.findByUserPaged(userId, limit)
        val unreadCount = notificationRepository.countUnreadByUser(userId)
        return NotificationListResponse(
            notifications = notifications.map { it.toResponse() },
            unreadCount = unreadCount
        )
    }

    fun getUnreadCount(userId: Long): Long {
        return notificationRepository.countUnreadByUser(userId)
    }

    @Transactional
    fun markAsRead(userId: Long, notificationId: Long): Boolean {
        return notificationRepository.markAsRead(notificationId, userId) > 0
    }

    @Transactional
    fun markAllAsRead(userId: Long): Int {
        return notificationRepository.markAllAsRead(userId)
    }

    // === 뉴스 수집 후 구독자 매칭 → 알림 생성 ===

    @Transactional
    fun createNotificationsForNews(
        newsId: String?,
        title: String,
        summary: String?,
        categories: List<String>,
        tickers: List<String>,
        sourceName: String,
        importance: Double,
        sourceUrl: String?
    ) {
        val matchedUserIds = mutableSetOf<Long>()
        val notifications = mutableListOf<NewsNotification>()

        // 카테고리 구독 매칭
        categories.forEach { category ->
            val subs = subscriptionRepository.findActiveByTypeAndValue("CATEGORY", category)
            subs.forEach { sub ->
                if (sub.notifyChannel == "IN_APP" && matchedUserIds.add(sub.userId)) {
                    notifications.add(
                        NewsNotification(
                            userId = sub.userId,
                            newsId = newsId,
                            categoryName = category,
                            title = title,
                            message = summary,
                            importance = importance,
                            sourceUrl = sourceUrl
                        )
                    )
                }
            }
        }

        // 티커 구독 매칭
        tickers.forEach { ticker ->
            val subs = subscriptionRepository.findActiveByTypeAndValue("TICKER", ticker)
            subs.forEach { sub ->
                if (sub.notifyChannel == "IN_APP" && matchedUserIds.add(sub.userId)) {
                    notifications.add(
                        NewsNotification(
                            userId = sub.userId,
                            newsId = newsId,
                            title = "[$ticker] $title",
                            message = summary,
                            importance = importance,
                            sourceUrl = sourceUrl
                        )
                    )
                }
            }
        }

        // 소스 구독 매칭
        val sourceSubs = subscriptionRepository.findActiveByTypeAndValue("SOURCE", sourceName)
        sourceSubs.forEach { sub ->
            if (sub.notifyChannel == "IN_APP" && matchedUserIds.add(sub.userId)) {
                notifications.add(
                    NewsNotification(
                        userId = sub.userId,
                        newsId = newsId,
                        title = title,
                        message = summary,
                        importance = importance,
                        sourceUrl = sourceUrl
                    )
                )
            }
        }

        // 배치 저장
        if (notifications.isNotEmpty()) {
            notificationRepository.saveAll(notifications)
            logger.info("뉴스 알림 생성: {} 명에게 전달 ({})", matchedUserIds.size, title.take(30))
        }
    }

    // === 유틸 ===

    private fun buildDisplayName(type: String, value: String, channel: String): String {
        val channelLabel = when (channel) {
            "PUSH" -> " (푸시)"
            "TELEGRAM" -> " (텔레그램)"
            else -> ""
        }
        return when (type) {
            "CATEGORY" -> "$value 알림$channelLabel"
            "TICKER" -> "\$$value 뉴스$channelLabel"
            "SOURCE" -> "$value 소스$channelLabel"
            else -> "$value$channelLabel"
        }
    }

    private fun NewsSubscription.toResponse() = SubscriptionResponse(
        id = id!!,
        type = subscriptionType,
        value = subscriptionValue,
        displayName = displayName,
        channel = notifyChannel,
        isActive = isActive
    )

    private fun NewsNotification.toResponse() = NotificationResponse(
        id = id!!,
        title = title,
        message = message,
        categoryName = categoryName,
        importance = importance,
        sourceUrl = sourceUrl,
        isRead = isRead,
        createdAt = createdAt?.format(formatter) ?: ""
    )

    companion object {
        private const val MAX_SUBSCRIPTIONS = 50
    }
}
