package com.quantjumpstock.core.adapter.input.rest.admin

import com.quantjumpstock.core.application.admin.AdminSubscriptionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

/**
 * 어드민용 구독 현황 조회 API
 * GET /api/v1/admin/subscriptions
 */
@RestController
@RequestMapping("/api/v1/admin/subscriptions")
@Tag(name = "Admin Subscriptions", description = "관리자용 구독 현황 API")
@PreAuthorize("hasRole('ADMIN')")
class AdminSubscriptionController(
    private val adminSubscriptionService: AdminSubscriptionService
) {

    @GetMapping
    @Operation(summary = "전체 구독 현황 조회", description = "전략별/유저별 구독 현황을 조회합니다.")
    fun getAllSubscriptions(
        @RequestParam(required = false) strategyId: Long?,
        @RequestParam(required = false) userId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<AdminSubscriptionListResponse> {
        return ResponseEntity.ok(
            adminSubscriptionService.getSubscriptions(strategyId, userId, page, size)
        )
    }
}

data class AdminSubscriptionSummary(
    val subscriptionId: Long,
    val userId: Long,
    val userLoginId: String,
    val strategyId: Long,
    val strategyName: String,
    val status: String,
    val alertEnabled: Boolean,
    val subscribedAt: LocalDateTime
)

data class AdminSubscriptionListResponse(
    val subscriptions: List<AdminSubscriptionSummary>,
    val total: Long,
    val activeCount: Long
)
