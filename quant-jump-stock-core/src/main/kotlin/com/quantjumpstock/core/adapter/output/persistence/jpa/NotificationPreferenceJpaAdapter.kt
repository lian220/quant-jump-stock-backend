package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.quantjumpstock.core.domain.notification.model.NotificationPreference
import com.quantjumpstock.core.domain.notification.port.output.NotificationPreferenceRepository
import org.springframework.stereotype.Component

@Component
class NotificationPreferenceJpaAdapter(
    private val jpaRepository: NotificationPreferenceJpaRepository
) : NotificationPreferenceRepository {

    override fun findByUserId(userId: Long): NotificationPreference? =
        jpaRepository.findByUserId(userId)?.toDomain()

    override fun findByUserIds(userIds: List<Long>): List<NotificationPreference> =
        jpaRepository.findByUserIdIn(userIds).map { it.toDomain() }

    override fun save(preference: NotificationPreference): NotificationPreference {
        val existing = jpaRepository.findByUserId(preference.userId)
        val entity = if (existing != null) {
            existing.apply {
                newsEnabled = preference.newsEnabled
                announcementEnabled = preference.announcementEnabled
                tradingSignalEnabled = preference.tradingSignalEnabled
                backtestEnabled = preference.backtestEnabled
                priceAlertEnabled = preference.priceAlertEnabled
                weeklyDigestEnabled = preference.weeklyDigestEnabled
                pushEnabled = preference.pushEnabled
            }
        } else {
            preference.toEntity()
        }
        return jpaRepository.save(entity).toDomain()
    }

    private fun NotificationPreferenceEntity.toDomain() = NotificationPreference(
        id = id,
        userId = userId,
        newsEnabled = newsEnabled,
        announcementEnabled = announcementEnabled,
        tradingSignalEnabled = tradingSignalEnabled,
        backtestEnabled = backtestEnabled,
        priceAlertEnabled = priceAlertEnabled,
        weeklyDigestEnabled = weeklyDigestEnabled,
        pushEnabled = pushEnabled,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun NotificationPreference.toEntity() = NotificationPreferenceEntity(
        id = id,
        userId = userId,
        newsEnabled = newsEnabled,
        announcementEnabled = announcementEnabled,
        tradingSignalEnabled = tradingSignalEnabled,
        backtestEnabled = backtestEnabled,
        priceAlertEnabled = priceAlertEnabled,
        weeklyDigestEnabled = weeklyDigestEnabled,
        pushEnabled = pushEnabled
    )
}
