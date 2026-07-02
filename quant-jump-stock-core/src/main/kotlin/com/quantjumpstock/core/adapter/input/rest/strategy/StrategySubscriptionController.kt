package com.quantjumpstock.core.adapter.input.rest.strategy

import com.quantjumpstock.core.application.subscription.*
import com.quantjumpstock.core.infrastructure.security.CurrentUser
import com.quantjumpstock.core.infrastructure.security.UnauthorizedException
import com.quantjumpstock.core.infrastructure.security.UserPrincipal
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
 *
 * SubscriptionException(동적 status + errorCode + extra)은 GlobalExceptionHandler 가
 * 기존 응답 형식 그대로 매핑한다.
 */
@RestController
@Tag(name = "Subscription", description = "전략 구독 API")
class StrategySubscriptionController(
    private val subscriptionService: StrategySubscriptionService
) {

    @PostMapping("/api/v1/strategies/{strategyId}/subscribe")
    @Operation(summary = "전략 구독", description = "전략을 구독합니다.")
    fun subscribe(
        @CurrentUser user: UserPrincipal?,
        @PathVariable strategyId: Long,
        @RequestBody(required = false) request: SubscribeRequest?
    ): ResponseEntity<Any> {
        val userId = requireUserId(user)
        val response = subscriptionService.subscribe(userId, strategyId, request?.universeType)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @DeleteMapping("/api/v1/strategies/{strategyId}/subscribe")
    @Operation(summary = "전략 구독 취소", description = "전략 구독을 취소합니다.")
    fun unsubscribe(
        @CurrentUser user: UserPrincipal?,
        @PathVariable strategyId: Long
    ): ResponseEntity<Any> {
        val userId = requireUserId(user)
        val response = subscriptionService.unsubscribe(userId, strategyId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/api/v1/subscriptions")
    @Operation(summary = "내 구독 목록 조회", description = "현재 사용자의 전략 구독 목록을 조회합니다.")
    fun getMySubscriptions(
        @CurrentUser user: UserPrincipal?
    ): ResponseEntity<Any> {
        val userId = requireUserId(user)
        val response = subscriptionService.getMySubscriptions(userId)
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/api/v1/subscriptions/{subscriptionId}/alert")
    @Operation(summary = "구독 알림 설정 변경", description = "구독의 알림 활성화 여부를 변경합니다.")
    fun updateAlert(
        @CurrentUser user: UserPrincipal?,
        @PathVariable subscriptionId: Long,
        @RequestBody request: AlertUpdateRequest
    ): ResponseEntity<Any> {
        val userId = requireUserId(user)
        val response = subscriptionService.updateAlert(userId, subscriptionId, request.alertEnabled)
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/api/v1/subscriptions/{subscriptionId}/broker-account")
    @Operation(summary = "구독 실행 계좌 변경", description = "전략 구독의 실행 계좌를 변경합니다 (Phase 1B v2.1).")
    fun updateBrokerAccount(
        @CurrentUser user: UserPrincipal?,
        @PathVariable subscriptionId: Long,
        @RequestBody request: BrokerAccountUpdateRequest,
    ): ResponseEntity<Any> {
        val userId = requireUserId(user)
        val response = subscriptionService.updateBrokerAccount(userId, subscriptionId, request.brokerAccountId)
        return ResponseEntity.ok(response)
    }

    private fun requireUserId(user: UserPrincipal?): String =
        user?.userId ?: throw UnauthorizedException("인증이 필요합니다.")
}
