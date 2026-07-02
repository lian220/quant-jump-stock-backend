package com.quantjumpstock.core.adapter.input.rest.portfolio

import com.quantjumpstock.core.application.portfolio.*
import com.quantjumpstock.core.infrastructure.security.CurrentUser
import com.quantjumpstock.core.infrastructure.security.UnauthorizedException
import com.quantjumpstock.core.infrastructure.security.UserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/strategies/{strategyId}/default-stocks")
@Tag(name = "Strategy Default Stock", description = "전략 기본 종목 관리 API")
class StrategyDefaultStockController(
    private val defaultStockService: StrategyDefaultStockService
) {

    @GetMapping
    @Operation(summary = "기본 종목 목록", description = "전략의 기본 구성 종목 목록을 조회합니다.")
    fun getDefaultStocks(
        @Parameter(description = "전략 ID") @PathVariable strategyId: Long
    ): ResponseEntity<DefaultStockListResponse> {
        val response = defaultStockService.getDefaultStocks(strategyId)
        return ResponseEntity.ok(response)
    }

    @PostMapping
    @Operation(summary = "기본 종목 추가", description = "전략에 기본 종목을 추가합니다.")
    fun addDefaultStock(
        @Parameter(description = "전략 ID") @PathVariable strategyId: Long,
        @CurrentUser user: UserPrincipal?,
        @RequestBody request: AddDefaultStockRequest
    ): ResponseEntity<Any> {
        requireAuthenticated(user)
        val response = defaultStockService.addDefaultStock(strategyId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PutMapping("/{stockId}")
    @Operation(summary = "기본 종목 비중 수정", description = "기본 종목의 비중을 수정합니다.")
    fun updateDefaultStock(
        @Parameter(description = "전략 ID") @PathVariable strategyId: Long,
        @Parameter(description = "종목 ID") @PathVariable stockId: Long,
        @CurrentUser user: UserPrincipal?,
        @RequestBody request: UpdateDefaultStockRequest
    ): ResponseEntity<Any> {
        requireAuthenticated(user)
        val response = defaultStockService.updateDefaultStock(strategyId, stockId, request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{stockId}")
    @Operation(summary = "기본 종목 삭제", description = "전략에서 기본 종목을 삭제합니다.")
    fun removeDefaultStock(
        @Parameter(description = "전략 ID") @PathVariable strategyId: Long,
        @Parameter(description = "종목 ID") @PathVariable stockId: Long,
        @CurrentUser user: UserPrincipal?
    ): ResponseEntity<PortfolioResponse> {
        requireAuthenticated(user)
        val response = defaultStockService.removeDefaultStock(strategyId, stockId)
        return ResponseEntity.ok(response)
    }

    @PutMapping
    @Operation(summary = "기본 종목 전체 교체", description = "전략의 기본 종목을 전체 교체합니다. 비중 합 100% 이내 검증.")
    fun replaceDefaultStocks(
        @Parameter(description = "전략 ID") @PathVariable strategyId: Long,
        @CurrentUser user: UserPrincipal?,
        @RequestBody request: ReplaceDefaultStocksRequest
    ): ResponseEntity<Any> {
        requireAuthenticated(user)
        val response = defaultStockService.replaceDefaultStocks(strategyId, request)
        return ResponseEntity.ok(response)
    }

    private fun requireAuthenticated(user: UserPrincipal?) {
        if (user == null) throw UnauthorizedException("인증이 필요합니다")
    }
}
