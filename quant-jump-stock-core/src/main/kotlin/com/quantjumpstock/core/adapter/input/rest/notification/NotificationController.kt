package com.quantjumpstock.core.adapter.input.rest.notification

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.news.SimpleResponse
import com.quantjumpstock.core.application.notification.NotificationListDto
import com.quantjumpstock.core.application.notification.NotificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "통합 알림 API")
class NotificationController(
    private val notificationService: NotificationService,
    private val authService: AuthService
) {

    @GetMapping
    @Operation(summary = "알림 목록 조회", description = "최신순으로 알림 목록을 조회합니다")
    fun getNotifications(
        @RequestHeader("Authorization") authorization: String,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<Any> {
        val userId = authService.resolveUserPk(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SimpleResponse(false, "인증이 필요합니다"))

        val notifications = notificationService.getUserNotifications(userId, limit)
        val unreadCount = notificationService.getUnreadCount(userId)
        return ResponseEntity.ok(NotificationListDto.from(notifications, unreadCount))
    }

    @GetMapping("/unread-count")
    @Operation(summary = "미읽음 알림 수 조회")
    fun getUnreadCount(
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<Any> {
        val userId = authService.resolveUserPk(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SimpleResponse(false, "인증이 필요합니다"))

        return ResponseEntity.ok(mapOf("unreadCount" to notificationService.getUnreadCount(userId)))
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "알림 읽음 처리")
    fun markAsRead(
        @RequestHeader("Authorization") authorization: String,
        @PathVariable id: Long
    ): ResponseEntity<SimpleResponse> {
        val userId = authService.resolveUserPk(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SimpleResponse(false, "인증이 필요합니다"))

        val success = notificationService.markAsRead(userId, id)
        return ResponseEntity.ok(SimpleResponse(success))
    }

    @PatchMapping("/read-all")
    @Operation(summary = "전체 알림 읽음 처리")
    fun markAllAsRead(
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<SimpleResponse> {
        val userId = authService.resolveUserPk(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SimpleResponse(false, "인증이 필요합니다"))

        notificationService.markAllAsRead(userId)
        return ResponseEntity.ok(SimpleResponse(true))
    }
}
