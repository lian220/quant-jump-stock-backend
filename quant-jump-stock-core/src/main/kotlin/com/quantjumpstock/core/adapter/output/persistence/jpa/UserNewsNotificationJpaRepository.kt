package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserNewsNotificationJpaRepository : JpaRepository<UserNewsNotificationEntity, Long> {

    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): List<UserNewsNotificationEntity>

    fun countByUserIdAndIsReadFalse(userId: Long): Long

    @Modifying
    @Query("UPDATE UserNewsNotificationEntity n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    fun markAllAsRead(userId: Long): Int

    @Modifying
    @Query("UPDATE UserNewsNotificationEntity n SET n.isRead = true WHERE n.id = :id AND n.userId = :userId")
    fun markAsRead(id: Long, userId: Long): Int
}
