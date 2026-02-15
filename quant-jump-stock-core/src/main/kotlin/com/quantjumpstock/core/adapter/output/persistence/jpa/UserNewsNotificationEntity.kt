package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "user_news_notifications")
class UserNewsNotificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "news_id", length = 100)
    val newsId: String? = null,

    @Column(name = "category_name", length = 100)
    val categoryName: String? = null,

    @Column(nullable = false, length = 500)
    val title: String,

    @Column(columnDefinition = "TEXT")
    val message: String? = null,

    @Column(nullable = false)
    val importance: Double = 0.0,

    @Column(name = "source_url", columnDefinition = "TEXT")
    val sourceUrl: String? = null,

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
