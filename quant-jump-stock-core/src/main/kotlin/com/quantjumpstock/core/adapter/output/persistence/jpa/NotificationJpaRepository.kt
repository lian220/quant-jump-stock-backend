package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface NotificationJpaRepository : JpaRepository<NotificationEntity, Long> {

    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): List<NotificationEntity>

    fun countByUserIdAndIsReadFalse(userId: Long): Long

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.isRead = true WHERE n.id = :id AND n.userId = :userId")
    fun markAsRead(id: Long, userId: Long): Int

    fun countByUserIdAndCreatedAtGreaterThanEqual(userId: Long, since: java.time.LocalDateTime): Long

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    fun markAllAsRead(userId: Long): Int
}
