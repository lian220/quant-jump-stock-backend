package com.quantjumpstock.core.adapter.input.rest.stock

import com.quantjumpstock.core.application.stock.*
import com.quantjumpstock.core.domain.model.stock.Market
import com.quantjumpstock.core.domain.model.stock.PriceHistoryPeriod
import com.quantjumpstock.core.infrastructure.security.CurrentUser
import com.quantjumpstock.core.infrastructure.security.UnauthorizedException
import com.quantjumpstock.core.infrastructure.security.UserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Stock", description = "종목 관리 API")
class StockController(
    private val stockService: StockService
) {

    // ===== 일반 사용자 API =====

    @GetMapping("/api/v1/stocks")
    @Operation(summary = "종목 검색/목록", description = "종목을 검색하고 목록을 조회합니다. 페이징, 시장/섹터 필터를 지원합니다.")
    fun searchStocks(
        @Parameter(description = "검색어 (티커, 종목명)") @RequestParam(required = false) query: String?,
        @Parameter(description = "시장 구분") @RequestParam(required = false) market: Market?,
        @Parameter(description = "섹터") @RequestParam(required = false) sector: String?,
        @Parameter(description = "활성 여부") @RequestParam(required = false, defaultValue = "true") isActive: Boolean?,
        @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<StockSearchResponse> {
        val response = stockService.searchStocks(query, market, sector, isActive, PageRequest.of(page, size))
        return ResponseEntity.ok(response)
    }

    @GetMapping("/api/v1/stocks/{id}")
    @Operation(summary = "종목 상세 조회", description = "종목 ID로 상세 정보를 조회합니다.")
    fun getStock(
        @Parameter(description = "종목 ID") @PathVariable id: Long
    ): ResponseEntity<StockDetailResponse> {
        val response = stockService.getStock(id)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/api/v1/stocks/{id}/price-history")
    @Operation(summary = "종목 가격 이력", description = "캔들 + MA/RSI/MACD 시계열을 조회합니다.")
    fun getPriceHistory(
        @Parameter(description = "종목 ID") @PathVariable id: Long,
        @Parameter(description = "기간") @RequestParam(defaultValue = "6m") period: PriceHistoryPeriod
    ): ResponseEntity<PriceHistoryResponse> {
        return ResponseEntity.ok(stockService.getPriceHistory(id, period))
    }

    // ===== 관리자 API =====
    // 인증/권한은 Spring Security(@PreAuthorize + JwtAuthenticationFilter)가 처리하고,
    // 도메인 예외 → HTTP 매핑은 GlobalExceptionHandler 가 담당한다.

    @PostMapping("/api/v1/admin/stocks")
    @Operation(summary = "종목 등록 (관리자)", description = "새로운 종목을 등록합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    fun createStock(
        @Valid @RequestBody request: CreateStockRequest
    ): ResponseEntity<StockResponse> {
        val response = stockService.createStock(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PutMapping("/api/v1/admin/stocks/{id}")
    @Operation(summary = "종목 수정 (관리자)", description = "종목 정보를 수정합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateStock(
        @Parameter(description = "종목 ID") @PathVariable id: Long,
        @Valid @RequestBody request: UpdateStockRequest
    ): ResponseEntity<StockResponse> {
        val response = stockService.updateStock(id, request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/api/v1/admin/stocks/{id}")
    @Operation(summary = "종목 삭제 (관리자)", description = "종목을 삭제합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteStock(
        @Parameter(description = "종목 ID") @PathVariable id: Long
    ): ResponseEntity<StockResponse> {
        val response = stockService.deleteStock(id)
        return ResponseEntity.ok(response)
    }

    @PutMapping("/api/v1/admin/stocks/{id}/designation")
    @Operation(summary = "종목 지정상태 변경 (관리자)", description = "종목의 지정 상태를 변경하고 이력을 기록합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    fun changeDesignation(
        @Parameter(description = "종목 ID") @PathVariable id: Long,
        @CurrentUser user: UserPrincipal?,
        @Valid @RequestBody request: ChangeDesignationRequest
    ): ResponseEntity<StockResponse> {
        val changedBy = user?.id?.takeIf { it > 0 } ?: throw UnauthorizedException("인증이 필요합니다")
        val response = stockService.changeDesignation(id, request, changedBy)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/api/v1/admin/stocks/{id}/designation-history")
    @Operation(summary = "종목 지정상태 변경 이력 (관리자)", description = "종목의 지정 상태 변경 이력을 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    fun getDesignationHistory(
        @Parameter(description = "종목 ID") @PathVariable id: Long
    ): ResponseEntity<List<DesignationHistoryResponse>> {
        val response = stockService.getDesignationHistory(id)
        return ResponseEntity.ok(response)
    }
}
