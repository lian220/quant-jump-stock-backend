package com.quantjumpstock.core.domain.notification.port.output

import com.quantjumpstock.core.domain.notification.model.NotificationPreference

interface NotificationPreferenceRepository {
    fun findByUserId(userId: Long): NotificationPreference?
    fun save(preference: NotificationPreference): NotificationPreference
}
