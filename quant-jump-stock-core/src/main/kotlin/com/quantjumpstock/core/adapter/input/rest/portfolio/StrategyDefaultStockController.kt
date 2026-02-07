package com.quantjumpstock.core.adapter.input.rest.portfolio

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.portfolio.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/strategies/{strategyId}/default-stocks")
@Tag(name = "Strategy Default Stock", description = "전략 기본 종목 관리 API")
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:4000"], allowCredentials = "true")
class StrategyDefaultStockController(
    private val defaultStockService: StrategyDefaultStockService,
    private val authService: AuthService
) {

    @GetMapping
    @Operation(summary = "기본 종목 목록", description = "전략의 기본 구성 종목 목록을 조회합니다.")
    fun getDefaultStocks(
        @Parameter(description = "전략 ID") @PathVariable strategyId: Long
    ): ResponseEntity<DefaultStockListResponse> {
        return try {
            val response = defaultStockService.getDefaultStocks(strategyId)
            ResponseEntity.ok(response)
        } catch (e: PortfolioException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    @PostMapping
    @Operation(summary = "기본 종목 추가", description = "전략에 기본 종목을 추가합니다.")
    fun addDefaultStock(
        @Parameter(description = "전략 ID") @PathVariable strategyId: Long,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: AddDefaultStockRequest
    ): ResponseEntity<Any> {
        extractUserId(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(PortfolioResponse(success = false, message = "인증이 필요합니다"))

        return try {
            val response = defaultStockService.addDefaultStock(strategyId, request)
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } catch (e: PortfolioException) {
            ResponseEntity.badRequest().body(
                PortfolioResponse(success = false, message = e.message)
            )
        }
    }

    @PutMapping("/{stockId}")
    @Operation(summary = "기본 종목 비중 수정", description = "기본 종목의 비중을 수정합니다.")
    fun updateDefaultStock(
        @Parameter(description = "전략 ID") @PathVariable strategyId: Long,
        @Parameter(description = "종목 ID") @PathVariable stockId: Long,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: UpdateDefaultStockRequest
    ): ResponseEntity<Any> {
        extractUserId(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(PortfolioResponse(success = false, message = "인증이 필요합니다"))

        return try {
            val response = defaultStockService.updateDefaultStock(strategyId, stockId, request)
            ResponseEntity.ok(response)
        } catch (e: PortfolioException) {
            ResponseEntity.badRequest().body(
                PortfolioResponse(success = false, message = e.message)
            )
        }
    }

    @DeleteMapping("/{stockId}")
    @Operation(summary = "기본 종목 삭제", description = "전략에서 기본 종목을 삭제합니다.")
    fun removeDefaultStock(
        @Parameter(description = "전략 ID") @PathVariable strategyId: Long,
        @Parameter(description = "종목 ID") @PathVariable stockId: Long,
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<PortfolioResponse> {
        extractUserId(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(PortfolioResponse(success = false, message = "인증이 필요합니다"))

        return try {
            val response = defaultStockService.removeDefaultStock(strategyId, stockId)
            ResponseEntity.ok(response)
        } catch (e: PortfolioException) {
            ResponseEntity.badRequest().body(
                PortfolioResponse(success = false, message = e.message)
            )
        }
    }

    @PutMapping
    @Operation(summary = "기본 종목 전체 교체", description = "전략의 기본 종목을 전체 교체합니다. 비중 합 100% 이내 검증.")
    fun replaceDefaultStocks(
        @Parameter(description = "전략 ID") @PathVariable strategyId: Long,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: ReplaceDefaultStocksRequest
    ): ResponseEntity<Any> {
        extractUserId(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(PortfolioResponse(success = false, message = "인증이 필요합니다"))

        return try {
            val response = defaultStockService.replaceDefaultStocks(strategyId, request)
            ResponseEntity.ok(response)
        } catch (e: PortfolioException) {
            ResponseEntity.badRequest().body(
                PortfolioResponse(success = false, message = e.message)
            )
        }
    }

    private fun extractUserId(authorization: String): String? {
        if (!authorization.startsWith("Bearer ")) return null
        val token = authorization.removePrefix("Bearer ")
        val loginResponse = authService.validateToken(token) ?: return null
        return loginResponse.user?.userId
    }
}
