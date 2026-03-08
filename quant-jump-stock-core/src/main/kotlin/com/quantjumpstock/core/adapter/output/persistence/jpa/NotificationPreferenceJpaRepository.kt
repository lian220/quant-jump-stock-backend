package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository

interface NotificationPreferenceJpaRepository : JpaRepository<NotificationPreferenceEntity, Long> {
    fun findByUserId(userId: Long): NotificationPreferenceEntity?
    fun findByUserIdIn(userIds: List<Long>): List<NotificationPreferenceEntity>
}
