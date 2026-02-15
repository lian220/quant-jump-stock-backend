package com.quantjumpstock.core.adapter.input.rest.news

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.news.*
import com.quantjumpstock.core.domain.port.output.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/news/subscriptions")
@Tag(name = "News Subscriptions", description = "뉴스 구독 및 알림 API")
class NewsSubscriptionController(
    private val subscriptionService: NewsSubscriptionService,
    private val authService: AuthService,
    private val userRepository: UserRepository
) {

    @PostMapping
    @Operation(summary = "뉴스 구독", description = "카테고리/티커/소스별 뉴스 알림을 구독합니다.")
    fun subscribe(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: SubscribeRequest
    ): ResponseEntity<Any> {
        val userId = extractUserIdAsLong(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SimpleResponse(false, "인증이 필요합니다"))

        return try {
            val response = subscriptionService.subscribe(userId, request)
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(SimpleResponse(false, e.message))
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "구독 해제", description = "뉴스 구독을 해제합니다.")
    fun unsubscribe(
        @RequestHeader("Authorization") authorization: String,
        @Parameter(description = "구독 ID") @PathVariable id: Long
    ): ResponseEntity<SimpleResponse> {
        val userId = extractUserIdAsLong(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SimpleResponse(false, "인증이 필요합니다"))

        val result = subscriptionService.unsubscribe(userId, id)
        return if (result) {
            ResponseEntity.ok(SimpleResponse(true, "구독이 해제되었습니다"))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(SimpleResponse(false, "구독을 찾을 수 없습니다"))
        }
    }

    @GetMapping
    @Operation(summary = "내 구독 목록", description = "로그인한 사용자의 뉴스 구독 목록을 조회합니다.")
    fun getSubscriptions(
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<Any> {
        val userId = extractUserIdAsLong(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SimpleResponse(false, "인증이 필요합니다"))

        return ResponseEntity.ok(subscriptionService.getUserSubscriptions(userId))
    }

    @GetMapping("/notifications")
    @Operation(summary = "알림 목록", description = "뉴스 알림 목록을 조회합니다.")
    fun getNotifications(
        @RequestHeader("Authorization") authorization: String,
        @RequestParam(defaultValue = "30") limit: Int
    ): ResponseEntity<Any> {
        val userId = extractUserIdAsLong(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SimpleResponse(false, "인증이 필요합니다"))

        return ResponseEntity.ok(subscriptionService.getUserNotifications(userId, limit))
    }

    @GetMapping("/notifications/unread-count")
    @Operation(summary = "읽지 않은 알림 수", description = "읽지 않은 뉴스 알림 수를 조회합니다.")
    fun getUnreadCount(
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<Any> {
        val userId = extractUserIdAsLong(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SimpleResponse(false, "인증이 필요합니다"))

        return ResponseEntity.ok(mapOf("unreadCount" to subscriptionService.getUnreadCount(userId)))
    }

    @PatchMapping("/notifications/{id}/read")
    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음 처리합니다.")
    fun markAsRead(
        @RequestHeader("Authorization") authorization: String,
        @Parameter(description = "알림 ID") @PathVariable id: Long
    ): ResponseEntity<SimpleResponse> {
        val userId = extractUserIdAsLong(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SimpleResponse(false, "인증이 필요합니다"))

        subscriptionService.markAsRead(userId, id)
        return ResponseEntity.ok(SimpleResponse(true))
    }

    @PatchMapping("/notifications/read-all")
    @Operation(summary = "전체 읽음 처리", description = "모든 알림을 읽음 처리합니다.")
    fun markAllAsRead(
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<SimpleResponse> {
        val userId = extractUserIdAsLong(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SimpleResponse(false, "인증이 필요합니다"))

        val count = subscriptionService.markAllAsRead(userId)
        return ResponseEntity.ok(SimpleResponse(true, "${count}개 알림을 읽음 처리했습니다"))
    }

    private fun extractUserId(authorization: String): String? {
        if (!authorization.startsWith("Bearer ")) return null
        val token = authorization.removePrefix("Bearer ")
        val loginResponse = authService.validateToken(token) ?: return null
        return loginResponse.user?.userId
    }

    private fun extractUserIdAsLong(authorization: String): Long? {
        val userId = extractUserId(authorization) ?: return null
        val user = userRepository.findByUserId(userId) ?: return null
        return user.id
    }
}
