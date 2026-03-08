package com.quantjumpstock.core.application.notification

import com.quantjumpstock.core.domain.notification.model.NotificationPreference
import com.quantjumpstock.core.domain.notification.port.output.NotificationPreferenceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationPreferenceService(
    private val preferenceRepository: NotificationPreferenceRepository
) {

    fun getPreferences(userId: Long): NotificationPreference =
        preferenceRepository.findByUserId(userId)
            ?: NotificationPreference.defaultFor(userId)

    @Transactional
    fun updatePreferences(userId: Long, update: NotificationPreferenceUpdate): NotificationPreference {
        val current = preferenceRepository.findByUserId(userId)
            ?: NotificationPreference.defaultFor(userId)

        val updated = current.copy(
            newsEnabled = update.newsEnabled ?: current.newsEnabled,
            announcementEnabled = update.announcementEnabled ?: current.announcementEnabled,
            tradingSignalEnabled = update.tradingSignalEnabled ?: current.tradingSignalEnabled,
            backtestEnabled = update.backtestEnabled ?: current.backtestEnabled,
            priceAlertEnabled = update.priceAlertEnabled ?: current.priceAlertEnabled,
            weeklyDigestEnabled = update.weeklyDigestEnabled ?: current.weeklyDigestEnabled,
            pushEnabled = update.pushEnabled ?: current.pushEnabled
        )

        return preferenceRepository.save(updated)
    }
}

data class NotificationPreferenceUpdate(
    val newsEnabled: Boolean? = null,
    val announcementEnabled: Boolean? = null,
    val tradingSignalEnabled: Boolean? = null,
    val backtestEnabled: Boolean? = null,
    val priceAlertEnabled: Boolean? = null,
    val weeklyDigestEnabled: Boolean? = null,
    val pushEnabled: Boolean? = null
)
