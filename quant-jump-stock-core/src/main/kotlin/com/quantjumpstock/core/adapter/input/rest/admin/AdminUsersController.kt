package com.quantjumpstock.core.adapter.input.rest.admin

import com.quantjumpstock.core.application.admin.AdminUserListResponse
import com.quantjumpstock.core.application.admin.AdminUserService
import com.quantjumpstock.core.application.admin.AdminUserTierInfo
import com.quantjumpstock.core.application.admin.AdminUserTierService
import com.quantjumpstock.core.application.admin.UpdateUserTierRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 관리자용 회원 관리 API
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin Users", description = "관리자용 회원 관리 API")
@PreAuthorize("hasRole('ADMIN')")
class AdminUsersController(
    private val adminUserService: AdminUserService,
    private val adminUserTierService: AdminUserTierService
) {

    @GetMapping
    @Operation(
        summary = "회원 목록 조회",
        description = "관리자용 회원 목록을 페이징하여 조회합니다. 이름/이메일 검색 및 상태 필터를 지원합니다."
    )
    fun getAllUsers(
        @Parameter(description = "페이지 번호 (0부터 시작)")
        @RequestParam(defaultValue = "0") page: Int,

        @Parameter(description = "페이지 크기 (최대 100)")
        @Max(100) @Min(1) @RequestParam(defaultValue = "20") size: Int,

        @Parameter(description = "검색어 (이름 또는 이메일)")
        @RequestParam(required = false) search: String?,

        @Parameter(description = "상태 필터 (ACTIVE, INACTIVE, SUSPENDED)")
        @RequestParam(required = false) status: String?
    ): ResponseEntity<AdminUserListResponse> {
        val response = adminUserService.getAllUsers(
            page = page,
            size = size,
            search = search,
            status = status
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/tiers/batch")
    @Operation(summary = "유저 티어 배치 조회", description = "여러 유저의 티어와 구독 현황을 한 번에 조회합니다.")
    fun getUserTiersBatch(
        @Parameter(description = "조회할 유저 ID 목록 (쉼표 구분, 최대 100)")
        @RequestParam userIds: List<Long>
    ): ResponseEntity<List<AdminUserTierInfo>> {
        if (userIds.size > 100) {
            return ResponseEntity.badRequest().build()
        }
        return ResponseEntity.ok(adminUserTierService.getUserTiersBatch(userIds))
    }

    @GetMapping("/{id}/tier")
    @Operation(summary = "유저 티어 조회", description = "특정 유저의 티어와 구독 현황을 조회합니다.")
    fun getUserTier(@PathVariable id: Long): ResponseEntity<AdminUserTierInfo> {
        return try {
            ResponseEntity.ok(adminUserTierService.getUserTier(id))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    @PatchMapping("/{id}/tier")
    @Operation(summary = "유저 티어 변경", description = "특정 유저의 티어를 변경합니다. (FREE ↔ PREMIUM)")
    fun updateUserTier(
        @PathVariable id: Long,
        @RequestBody request: UpdateUserTierRequest,
        authentication: Authentication
    ): ResponseEntity<AdminUserTierInfo> {
        return try {
            val updatedBy = authentication.name ?: "admin"
            ResponseEntity.ok(adminUserTierService.updateUserTier(id, request, updatedBy))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }
}
