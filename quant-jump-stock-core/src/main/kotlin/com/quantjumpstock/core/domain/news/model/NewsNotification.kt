package com.quantjumpstock.core.domain.news.model

import java.time.LocalDateTime

data class NewsNotification(
    val id: Long? = null,
    val userId: Long,
    val newsId: String? = null,
    val categoryName: String? = null,
    val title: String,
    val message: String? = null,
    val importance: Double = 0.0,
    val sourceUrl: String? = null,
    val isRead: Boolean = false,
    val createdAt: LocalDateTime? = null
)
