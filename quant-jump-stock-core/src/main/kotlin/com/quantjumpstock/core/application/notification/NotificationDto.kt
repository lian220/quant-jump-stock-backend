package com.quantjumpstock.core.application.notification

import com.quantjumpstock.core.domain.notification.model.Notification
import io.swagger.v3.oas.annotations.media.Schema
import java.time.format.DateTimeFormatter

@Schema(description = "알림 목록 응답")
data class NotificationListDto(
    @Schema(description = "알림 목록")
    val notifications: List<NotificationItemDto>,
    @Schema(description = "미읽음 개수")
    val unreadCount: Long
) {
    companion object {
        fun from(notifications: List<Notification>, unreadCount: Long) = NotificationListDto(
            notifications = notifications.map { NotificationItemDto.from(it) },
            unreadCount = unreadCount
        )
    }
}

@Schema(description = "알림 항목")
data class NotificationItemDto(
    val id: Long,
    val type: String,
    val priority: String,
    val title: String,
    val message: String?,
    val actionUrl: String?,
    val metadata: Map<String, Any>?,
    val isRead: Boolean,
    val createdAt: String
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        fun from(n: Notification) = NotificationItemDto(
            id = n.id!!,
            type = n.type.name,
            priority = n.priority.name,
            title = n.title,
            message = n.message,
            actionUrl = n.actionUrl,
            metadata = n.metadata,
            isRead = n.isRead,
            createdAt = n.createdAt?.format(formatter) ?: ""
        )
    }
}
