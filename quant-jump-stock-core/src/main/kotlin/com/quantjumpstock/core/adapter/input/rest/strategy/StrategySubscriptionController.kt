package com.quantjumpstock.core.adapter.input.rest.strategy

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.subscription.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 전략 구독 API (SCRUM-206, 207, 208)
 * POST   /api/v1/strategies/{id}/subscribe
 * DELETE /api/v1/strategies/{id}/subscribe
 * GET    /api/v1/subscriptions
 * PATCH  /api/v1/subscriptions/{id}/alert
 */
@RestController
@Tag(name = "Subscription", description = "전략 구독 API")
class StrategySubscriptionController(
    private val subscriptionService: StrategySubscriptionService,
    private val authService: AuthService
) {

    @PostMapping("/api/v1/strategies/{strategyId}/subscribe")
    @Operation(summary = "전략 구독", description = "전략을 구독합니다.")
    fun subscribe(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable strategyId: Long,
        @RequestBody(required = false) request: SubscribeRequest?
    ): ResponseEntity<Any> {
        val userId = extractUserId(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "UNAUTHORIZED", "message" to "인증이 필요합니다."))

        return try {
            val response = subscriptionService.subscribe(userId, strategyId, request?.universeType)
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } catch (e: SubscriptionException) {
            ResponseEntity.status(e.httpStatus)
                .body(buildErrorBody(e))
        }
    }

    @DeleteMapping("/api/v1/strategies/{strategyId}/subscribe")
    @Operation(summary = "전략 구독 취소", description = "전략 구독을 취소합니다.")
    fun unsubscribe(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable strategyId: Long
    ): ResponseEntity<Any> {
        val userId = extractUserId(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "UNAUTHORIZED", "message" to "인증이 필요합니다."))

        return try {
            val response = subscriptionService.unsubscribe(userId, strategyId)
            ResponseEntity.ok(response)
        } catch (e: SubscriptionException) {
            ResponseEntity.status(e.httpStatus)
                .body(buildErrorBody(e))
        }
    }

    @GetMapping("/api/v1/subscriptions")
    @Operation(summary = "내 구독 목록 조회", description = "현재 사용자의 전략 구독 목록을 조회합니다.")
    fun getMySubscriptions(
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<Any> {
        val userId = extractUserId(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "UNAUTHORIZED", "message" to "인증이 필요합니다."))

        return try {
            val response = subscriptionService.getMySubscriptions(userId)
            ResponseEntity.ok(response)
        } catch (e: SubscriptionException) {
            ResponseEntity.status(e.httpStatus)
                .body(buildErrorBody(e))
        }
    }

    @PatchMapping("/api/v1/subscriptions/{subscriptionId}/alert")
    @Operation(summary = "구독 알림 설정 변경", description = "구독의 알림 활성화 여부를 변경합니다.")
    fun updateAlert(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable subscriptionId: Long,
        @RequestBody request: AlertUpdateRequest
    ): ResponseEntity<Any> {
        val userId = extractUserId(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "UNAUTHORIZED", "message" to "인증이 필요합니다."))

        return try {
            val response = subscriptionService.updateAlert(userId, subscriptionId, request.alertEnabled)
            ResponseEntity.ok(response)
        } catch (e: SubscriptionException) {
            ResponseEntity.status(e.httpStatus)
                .body(buildErrorBody(e))
        }
    }

    private fun extractUserId(authorization: String?): String? {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null
        val token = authorization.removePrefix("Bearer ")
        return authService.validateToken(token)?.user?.userId
    }

    private fun buildErrorBody(e: SubscriptionException): Map<String, Any> {
        val body = mutableMapOf<String, Any>(
            "error" to e.errorCode,
            "message" to (e.message ?: "오류가 발생했습니다.")
        )
        body.putAll(e.extra)
        return body
    }
}
