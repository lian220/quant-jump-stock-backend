package com.quantjumpstock.core.domain.news.model

import java.time.LocalDateTime

data class NewsSubscription(
    val id: Long? = null,
    val userId: Long,
    val subscriptionType: String,
    val subscriptionValue: String,
    val displayName: String? = null,
    val notifyChannel: String = "IN_APP",
    val isActive: Boolean = true,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
