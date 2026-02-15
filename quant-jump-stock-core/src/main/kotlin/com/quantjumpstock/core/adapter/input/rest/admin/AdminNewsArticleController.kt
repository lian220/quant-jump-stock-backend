package com.quantjumpstock.core.adapter.input.rest.admin

import com.quantjumpstock.core.application.admin.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

/**
 * 관리자용 뉴스 기사 관리 API
 */
@RestController
@RequestMapping("/api/v1/admin/news/articles")
@Tag(name = "Admin News Articles", description = "관리자용 뉴스 기사 관리 API")
@PreAuthorize("hasRole('ADMIN')")
class AdminNewsArticleController(
    private val adminNewsArticleService: AdminNewsArticleService
) {

    @GetMapping
    @Operation(summary = "기사 목록 조회", description = "페이지네이션 및 필터링을 지원하는 기사 목록 조회")
    fun getArticles(
        @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기 (1~100)") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "출처 필터") @RequestParam(required = false) source: String?,
        @Parameter(description = "태그 필터") @RequestParam(required = false) tag: String?,
        @Parameter(description = "티커 필터") @RequestParam(required = false) ticker: String?,
        @Parameter(description = "시작일") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) dateFrom: LocalDateTime?,
        @Parameter(description = "종료일") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) dateTo: LocalDateTime?,
        @Parameter(description = "숨김 포함 여부") @RequestParam(defaultValue = "true") includeHidden: Boolean,
        @Parameter(description = "정렬 기준") @RequestParam(defaultValue = "createdAt") sortBy: String,
        @Parameter(description = "정렬 방향") @RequestParam(defaultValue = "desc") sortDirection: String
    ): ResponseEntity<AdminNewsArticleListResponse> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val safeSortBy = if (sortBy in ALLOWED_SORT_FIELDS) sortBy else "createdAt"
        return ResponseEntity.ok(
            adminNewsArticleService.getArticles(
                page = safePage,
                size = safeSize,
                source = source,
                tag = tag,
                ticker = ticker,
                dateFrom = dateFrom,
                dateTo = dateTo,
                includeHidden = includeHidden,
                sortBy = safeSortBy,
                sortDirection = sortDirection
            )
        )
    }

    @GetMapping("/stats")
    @Operation(summary = "기사 통계 조회", description = "전체/활성/숨김 기사 수 및 평균 중요도")
    fun getStats(): ResponseEntity<AdminNewsArticleStatsResponse> {
        return ResponseEntity.ok(adminNewsArticleService.getStats())
    }

    @GetMapping("/{id}")
    @Operation(summary = "기사 상세 조회", description = "ID로 기사 상세 정보를 조회합니다.")
    fun getArticleById(
        @Parameter(description = "기사 ID") @PathVariable id: String
    ): ResponseEntity<AdminNewsArticle> {
        return ResponseEntity.ok(adminNewsArticleService.getArticleById(id))
    }

    @PostMapping
    @Operation(summary = "수동 기사 생성", description = "관리자가 수동으로 뉴스 기사를 생성합니다.")
    fun createArticle(
        @RequestBody request: CreateNewsArticleRequest
    ): ResponseEntity<AdminNewsArticle> {
        return ResponseEntity.ok(adminNewsArticleService.createArticle(request))
    }

    @PatchMapping("/{id}")
    @Operation(summary = "기사 수정", description = "기사의 메타데이터를 수정합니다.")
    fun updateArticle(
        @Parameter(description = "기사 ID") @PathVariable id: String,
        @RequestBody request: UpdateNewsArticleRequest
    ): ResponseEntity<AdminNewsArticle> {
        return ResponseEntity.ok(adminNewsArticleService.updateArticle(id, request))
    }

    @PatchMapping("/{id}/hide")
    @Operation(summary = "기사 숨김", description = "기사를 숨김 처리합니다 (soft delete).")
    fun hideArticle(
        @Parameter(description = "기사 ID") @PathVariable id: String
    ): ResponseEntity<Map<String, Any>> {
        adminNewsArticleService.hideArticle(id)
        return ResponseEntity.ok(mapOf("success" to true, "message" to "기사가 숨김 처리되었습니다."))
    }

    @PatchMapping("/{id}/restore")
    @Operation(summary = "기사 숨김 해제", description = "숨김 처리된 기사를 복원합니다.")
    fun unhideArticle(
        @Parameter(description = "기사 ID") @PathVariable id: String
    ): ResponseEntity<Map<String, Any>> {
        adminNewsArticleService.unhideArticle(id)
        return ResponseEntity.ok(mapOf("success" to true, "message" to "기사가 복원되었습니다."))
    }

    companion object {
        private val ALLOWED_SORT_FIELDS = setOf("createdAt", "importanceScore", "source", "viewCount")
    }
}
