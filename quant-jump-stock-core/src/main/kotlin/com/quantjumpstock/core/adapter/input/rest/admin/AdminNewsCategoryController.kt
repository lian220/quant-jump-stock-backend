package com.quantjumpstock.core.adapter.input.rest.admin

import com.quantjumpstock.core.application.admin.AdminCategoryDto
import com.quantjumpstock.core.application.admin.AdminCategoryListResponse
import com.quantjumpstock.core.application.admin.AdminNewsCategoryService
import com.quantjumpstock.core.application.admin.CreateCategoryRequest
import com.quantjumpstock.core.application.admin.UpdateCategoryRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/**
 * 관리자용 뉴스 카테고리 관리 API
 */
@RestController
@RequestMapping("/api/v1/admin/news/categories")
@Tag(name = "Admin News Categories", description = "관리자용 뉴스 카테고리 관리 API")
@PreAuthorize("hasRole('ADMIN')")
class AdminNewsCategoryController(
    private val adminNewsCategoryService: AdminNewsCategoryService
) {

    @GetMapping
    @Operation(summary = "전체 카테고리 목록 조회", description = "비활성 카테고리 포함 전체 목록을 조회합니다.")
    fun getAllCategories(
        @Parameter(description = "비활성 포함 여부")
        @RequestParam(defaultValue = "true") includeInactive: Boolean
    ): ResponseEntity<AdminCategoryListResponse> {
        return ResponseEntity.ok(adminNewsCategoryService.getAllCategories(includeInactive))
    }

    @PostMapping
    @Operation(summary = "카테고리 생성", description = "새 뉴스 카테고리를 생성합니다.")
    fun createCategory(
        @RequestBody request: CreateCategoryRequest
    ): ResponseEntity<AdminCategoryDto> {
        return ResponseEntity.ok(adminNewsCategoryService.createCategory(request))
    }

    @PatchMapping("/{id}")
    @Operation(summary = "카테고리 수정", description = "카테고리 정보를 부분 수정합니다.")
    fun updateCategory(
        @Parameter(description = "카테고리 ID") @PathVariable id: Long,
        @RequestBody request: UpdateCategoryRequest
    ): ResponseEntity<AdminCategoryDto> {
        return ResponseEntity.ok(adminNewsCategoryService.updateCategory(id, request))
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "카테고리 노출 토글", description = "카테고리의 활성/비활성 상태를 토글합니다.")
    fun toggleCategory(
        @Parameter(description = "카테고리 ID") @PathVariable id: Long
    ): ResponseEntity<AdminCategoryDto> {
        return ResponseEntity.ok(adminNewsCategoryService.toggleCategory(id))
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "카테고리 삭제", description = "카테고리를 삭제합니다.")
    fun deleteCategory(
        @Parameter(description = "카테고리 ID") @PathVariable id: Long
    ): ResponseEntity<Map<String, Any>> {
        adminNewsCategoryService.deleteCategory(id)
        return ResponseEntity.ok(mapOf("success" to true, "message" to "카테고리가 삭제되었습니다."))
    }
}
