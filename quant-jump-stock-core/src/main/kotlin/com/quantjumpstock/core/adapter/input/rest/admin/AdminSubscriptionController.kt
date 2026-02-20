package com.quantjumpstock.core.adapter.input.rest.admin

import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategySubscriptionJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.SubscriptionStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
    private val jpaRepository: StrategySubscriptionJpaRepository
) {

    @GetMapping
    @Operation(summary = "전체 구독 현황 조회", description = "전략별/유저별 구독 현황을 조회합니다.")
    fun getAllSubscriptions(
        @RequestParam(required = false) strategyId: Long?,
        @RequestParam(required = false) userId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<AdminSubscriptionListResponse> {
        val safeSize = size.coerceIn(1, 100)
        val pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "subscribedAt"))

        val items = when {
            strategyId != null -> jpaRepository.findByStrategyId(strategyId)
                .map { it.toAdminSummary() }
            userId != null -> jpaRepository.findByUserId(userId)
                .map { it.toAdminSummary() }
            else -> {
                // 전체 조회: 활성 구독만 (페이징)
                val allActive = jpaRepository.findAll().filter { it.status == SubscriptionStatus.ACTIVE }
                allActive.map { it.toAdminSummary() }
            }
        }

        val activeCount = items.count { it.status == "ACTIVE" }

        return ResponseEntity.ok(
            AdminSubscriptionListResponse(
                subscriptions = items,
                total = items.size.toLong(),
                activeCount = activeCount.toLong()
            )
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

private fun com.quantjumpstock.core.adapter.output.persistence.jpa.StrategySubscriptionEntity.toAdminSummary() =
    AdminSubscriptionSummary(
        subscriptionId = id ?: 0L,
        userId = user.id ?: 0L,
        userLoginId = user.userId,
        strategyId = strategy.id ?: 0L,
        strategyName = strategy.name,
        status = status.name,
        alertEnabled = notifySignals && notifyRebalance,
        subscribedAt = subscribedAt
    )
