package com.quantjumpstock.core.application.news

import io.swagger.v3.oas.annotations.media.Schema

// === 요청 DTO ===

@Schema(description = "뉴스 구독 요청")
data class SubscribeRequest(
    @Schema(description = "구독 유형", example = "CATEGORY", allowableValues = ["CATEGORY", "TICKER", "SOURCE"])
    val type: String,

    @Schema(description = "구독 대상 값", example = "속보")
    val value: String,

    @Schema(description = "알림 채널", example = "IN_APP", allowableValues = ["IN_APP", "PUSH", "TELEGRAM"])
    val channel: String = "IN_APP"
)

// === 응답 DTO ===

@Schema(description = "구독 정보")
data class SubscriptionResponse(
    val id: Long,
    val type: String,
    val value: String,
    val displayName: String?,
    val channel: String,
    val isActive: Boolean
)

@Schema(description = "구독 목록 응답")
data class SubscriptionListResponse(
    val subscriptions: List<SubscriptionResponse>,
    val total: Int
)

@Schema(description = "알림 정보")
data class NotificationResponse(
    val id: Long,
    val title: String,
    val message: String?,
    val categoryName: String?,
    val importance: Double,
    val sourceUrl: String?,
    val isRead: Boolean,
    val createdAt: String
)

@Schema(description = "알림 목록 응답")
data class NotificationListResponse(
    val notifications: List<NotificationResponse>,
    val unreadCount: Long
)
