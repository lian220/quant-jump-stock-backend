package com.quantjumpstock.core.adapter.input.rest.admin

import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyStatus
import com.quantjumpstock.core.application.admin.AdminStrategyListResponse
import com.quantjumpstock.core.application.admin.AdminStrategyService
import com.quantjumpstock.core.application.admin.AdminStrategyStatsResponse
import com.quantjumpstock.core.application.admin.ChangeStrategyStatusRequest
import com.quantjumpstock.core.application.admin.ChangeStrategyStatusResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 관리자용 전략 관리 API
 * 백오피스에서 전략을 조회하고 승인/반려/발행하는 API입니다.
 */
@RestController
@RequestMapping("/api/v1/admin/strategies")
@Tag(name = "Admin Strategy", description = "관리자용 전략 관리 API")
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:4000"], allowCredentials = "true")
class AdminStrategyController(
    private val adminStrategyService: AdminStrategyService
) {

    /**
     * 전체 전략 목록 조회 (관리자용)
     * GET /api/v1/admin/strategies
     */
    @GetMapping
    @Operation(
        summary = "전체 전략 목록 조회",
        description = "관리자용 전체 전략 목록을 페이징하여 조회합니다. 상태 및 카테고리로 필터링할 수 있습니다."
    )
    fun getAllStrategies(
        @Parameter(description = "페이지 번호 (0부터 시작)")
        @RequestParam(defaultValue = "0") page: Int,

        @Parameter(description = "페이지 크기")
        @RequestParam(defaultValue = "20") size: Int,

        @Parameter(description = "상태 필터 (DRAFT, PENDING_REVIEW, APPROVED, PUBLISHED, REJECTED, ARCHIVED)")
        @RequestParam(required = false) status: StrategyStatus?,

        @Parameter(description = "카테고리 코드 필터")
        @RequestParam(required = false) categoryCode: String?,

        @Parameter(description = "정렬 기준 (createdAt, updatedAt, name, subscriberCount)")
        @RequestParam(defaultValue = "createdAt") sortBy: String,

        @Parameter(description = "정렬 방향 (asc, desc)")
        @RequestParam(defaultValue = "desc") sortDirection: String
    ): ResponseEntity<AdminStrategyListResponse> {
        val response = adminStrategyService.getAllStrategies(
            page = page,
            size = size,
            status = status,
            categoryCode = categoryCode,
            sortBy = sortBy,
            sortDirection = sortDirection
        )
        return ResponseEntity.ok(response)
    }

    /**
     * 전략 상태 변경 (승인/반려/발행 등)
     * PATCH /api/v1/admin/strategies/{id}/status
     */
    @PatchMapping("/{id}/status")
    @Operation(
        summary = "전략 상태 변경",
        description = "전략의 상태를 변경합니다 (승인, 반려, 발행 등). 반려 시 사유를 입력할 수 있습니다."
    )
    fun changeStatus(
        @Parameter(description = "전략 ID")
        @PathVariable id: Long,

        @RequestBody request: ChangeStrategyStatusRequest
    ): ResponseEntity<ChangeStrategyStatusResponse> {
        return try {
            val response = adminStrategyService.changeStatus(id, request)
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(
                ChangeStrategyStatusResponse(
                    success = false,
                    strategyId = id,
                    previousStatus = StrategyStatus.DRAFT,
                    newStatus = request.status,
                    message = e.message
                )
            )
        }
    }

    /**
     * 전략 승인
     * POST /api/v1/admin/strategies/{id}/approve
     */
    @PostMapping("/{id}/approve")
    @Operation(
        summary = "전략 승인",
        description = "검토 대기 중인 전략을 승인합니다."
    )
    fun approveStrategy(
        @Parameter(description = "전략 ID")
        @PathVariable id: Long
    ): ResponseEntity<ChangeStrategyStatusResponse> {
        val request = ChangeStrategyStatusRequest(status = StrategyStatus.APPROVED)
        return changeStatus(id, request)
    }

    /**
     * 전략 반려
     * POST /api/v1/admin/strategies/{id}/reject
     */
    @PostMapping("/{id}/reject")
    @Operation(
        summary = "전략 반려",
        description = "검토 대기 중인 전략을 반려합니다."
    )
    fun rejectStrategy(
        @Parameter(description = "전략 ID")
        @PathVariable id: Long,

        @Parameter(description = "반려 사유")
        @RequestParam(required = false) reason: String?
    ): ResponseEntity<ChangeStrategyStatusResponse> {
        val request = ChangeStrategyStatusRequest(status = StrategyStatus.REJECTED, reason = reason)
        return changeStatus(id, request)
    }

    /**
     * 전략 발행
     * POST /api/v1/admin/strategies/{id}/publish
     */
    @PostMapping("/{id}/publish")
    @Operation(
        summary = "전략 발행",
        description = "승인된 전략을 마켓플레이스에 발행합니다."
    )
    fun publishStrategy(
        @Parameter(description = "전략 ID")
        @PathVariable id: Long
    ): ResponseEntity<ChangeStrategyStatusResponse> {
        val request = ChangeStrategyStatusRequest(status = StrategyStatus.PUBLISHED)
        return changeStatus(id, request)
    }

    /**
     * 전략 통계 조회
     * GET /api/v1/admin/strategies/stats
     */
    @GetMapping("/stats")
    @Operation(
        summary = "전략 통계 조회",
        description = "전체 전략 수, 검토 대기 수, 발행 수, 총 구독자 수 등 통계를 조회합니다."
    )
    fun getStats(): ResponseEntity<AdminStrategyStatsResponse> {
        val response = adminStrategyService.getStats()
        return ResponseEntity.ok(response)
    }
}
