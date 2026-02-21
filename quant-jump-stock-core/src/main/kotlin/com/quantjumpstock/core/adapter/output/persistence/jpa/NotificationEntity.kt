package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.quantjumpstock.core.domain.notification.model.NotificationPriority
import com.quantjumpstock.core.domain.notification.model.NotificationType
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "notifications")
class NotificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: NotificationType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val priority: NotificationPriority = NotificationPriority.NORMAL,

    @Column(nullable = false, length = 500)
    val title: String,

    @Column(columnDefinition = "TEXT")
    val message: String? = null,

    @Column(name = "action_url", length = 500)
    val actionUrl: String? = null,

    @Column(columnDefinition = "TEXT")
    val metadata: String? = null,

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
