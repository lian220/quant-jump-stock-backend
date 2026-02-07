package com.quantjumpstock.core.adapter.input.rest.portfolio

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.portfolio.*
import com.quantjumpstock.core.domain.port.output.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/portfolios")
@Tag(name = "User Portfolio", description = "사용자 포트폴리오 관리 API")
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:4000"], allowCredentials = "true")
class UserPortfolioController(
    private val userPortfolioService: UserPortfolioService,
    private val authService: AuthService,
    private val userRepository: UserRepository
) {

    @GetMapping
    @Operation(summary = "내 포트폴리오 목록", description = "로그인한 사용자의 포트폴리오 목록을 조회합니다.")
    fun getUserPortfolios(
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<List<UserPortfolioResponse>> {
        val userId = extractUserIdAsLong(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        return try {
            val response = userPortfolioService.getUserPortfolios(userId)
            ResponseEntity.ok(response)
        } catch (e: PortfolioException) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/{id}/stocks")
    @Operation(summary = "포트폴리오 종목 목록", description = "포트폴리오의 종목 목록을 조회합니다.")
    fun getPortfolioStocks(
        @Parameter(description = "포트폴리오 ID") @PathVariable id: Long,
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<PortfolioStockListResponse> {
        val userId = extractUserIdAsLong(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        return try {
            val response = userPortfolioService.getPortfolioStocks(id, userId)
            ResponseEntity.ok(response)
        } catch (e: PortfolioException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    @PostMapping("/{id}/stocks")
    @Operation(summary = "포트폴리오 종목 추가", description = "포트폴리오에 종목을 추가합니다.")
    fun addPortfolioStock(
        @Parameter(description = "포트폴리오 ID") @PathVariable id: Long,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: AddPortfolioStockRequest
    ): ResponseEntity<Any> {
        val userId = extractUserIdAsLong(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(PortfolioResponse(success = false, message = "인증이 필요합니다"))

        return try {
            val response = userPortfolioService.addPortfolioStock(id, request, userId)
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } catch (e: PortfolioException) {
            ResponseEntity.badRequest().body(
                PortfolioResponse(success = false, message = e.message)
            )
        }
    }

    @PutMapping("/{id}/stocks/{stockId}")
    @Operation(summary = "포트폴리오 종목 비중 수정", description = "포트폴리오 종목의 비중을 수정합니다.")
    fun updatePortfolioStock(
        @Parameter(description = "포트폴리오 ID") @PathVariable id: Long,
        @Parameter(description = "종목 ID") @PathVariable stockId: Long,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: UpdatePortfolioStockRequest
    ): ResponseEntity<Any> {
        val userId = extractUserIdAsLong(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(PortfolioResponse(success = false, message = "인증이 필요합니다"))

        return try {
            val response = userPortfolioService.updatePortfolioStock(id, stockId, request, userId)
            ResponseEntity.ok(response)
        } catch (e: PortfolioException) {
            ResponseEntity.badRequest().body(
                PortfolioResponse(success = false, message = e.message)
            )
        }
    }

    @DeleteMapping("/{id}/stocks/{stockId}")
    @Operation(summary = "포트폴리오 종목 삭제", description = "포트폴리오에서 종목을 삭제합니다.")
    fun removePortfolioStock(
        @Parameter(description = "포트폴리오 ID") @PathVariable id: Long,
        @Parameter(description = "종목 ID") @PathVariable stockId: Long,
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<PortfolioResponse> {
        val userId = extractUserIdAsLong(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(PortfolioResponse(success = false, message = "인증이 필요합니다"))

        return try {
            val response = userPortfolioService.removePortfolioStock(id, stockId, userId)
            ResponseEntity.ok(response)
        } catch (e: PortfolioException) {
            ResponseEntity.badRequest().body(
                PortfolioResponse(success = false, message = e.message)
            )
        }
    }

    @PostMapping("/from-strategy/{strategyId}")
    @Operation(summary = "전략 기반 포트폴리오 생성", description = "전략의 기본 종목을 복사하여 사용자 포트폴리오를 생성합니다.")
    fun createFromStrategy(
        @Parameter(description = "전략 ID") @PathVariable strategyId: Long,
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<Any> {
        val userId = extractUserIdAsLong(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(PortfolioResponse(success = false, message = "인증이 필요합니다"))

        return try {
            val response = userPortfolioService.createFromStrategy(userId, strategyId)
            ResponseEntity.status(HttpStatus.CREATED).body(response)
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

    private fun extractUserIdAsLong(authorization: String): Long? {
        val userId = extractUserId(authorization) ?: return null
        val user = userRepository.findByUserId(userId) ?: return null
        return user.id
    }
}
