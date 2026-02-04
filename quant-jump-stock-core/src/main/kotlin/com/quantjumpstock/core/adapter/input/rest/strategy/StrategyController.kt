package com.quantjumpstock.core.adapter.input.rest.strategy

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.strategy.CreateStrategyRequest
import com.quantjumpstock.core.application.strategy.MyStrategiesResponse
import com.quantjumpstock.core.application.strategy.StrategyDetailResponse
import com.quantjumpstock.core.application.strategy.StrategyException
import com.quantjumpstock.core.application.strategy.StrategyResponse
import com.quantjumpstock.core.application.strategy.StrategyService
import com.quantjumpstock.core.application.strategy.UpdateStrategyRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Strategy Controller
 * 전략 CRUD API
 */
@RestController
@RequestMapping("/api/v1/strategies")
@Tag(name = "Strategy", description = "전략 관리 API")
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:4000"], allowCredentials = "true")
class StrategyController(
    private val strategyService: StrategyService,
    private val authService: AuthService
) {

    /**
     * 전략 생성
     * POST /api/v1/strategies
     */
    @PostMapping
    @Operation(
        summary = "전략 생성",
        description = "새로운 투자 전략을 생성합니다. 인증이 필요합니다."
    )
    fun createStrategy(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: CreateStrategyRequest
    ): ResponseEntity<StrategyResponse> {
        val userId = extractUserId(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(StrategyResponse(success = false, message = "인증이 필요합니다"))

        return try {
            val response = strategyService.createStrategy(userId, request)
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } catch (e: StrategyException) {
            ResponseEntity.badRequest().body(
                StrategyResponse(success = false, message = e.message)
            )
        }
    }

    /**
     * 전략 상세 조회
     * GET /api/v1/strategies/{id}
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "전략 상세 조회",
        description = "전략 ID로 상세 정보를 조회합니다. 비공개 전략은 소유자만 조회 가능합니다."
    )
    fun getStrategy(
        @Parameter(description = "전략 ID")
        @PathVariable id: Long,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<StrategyDetailResponse> {
        val userId = authorization?.let { extractUserId(it) }

        return try {
            val response = strategyService.getStrategy(id, userId)
            ResponseEntity.ok(response)
        } catch (e: StrategyException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    /**
     * 전략 수정
     * PUT /api/v1/strategies/{id}
     */
    @PutMapping("/{id}")
    @Operation(
        summary = "전략 수정",
        description = "전략을 수정합니다. 소유자만 수정 가능합니다."
    )
    fun updateStrategy(
        @Parameter(description = "전략 ID")
        @PathVariable id: Long,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: UpdateStrategyRequest
    ): ResponseEntity<StrategyResponse> {
        val userId = extractUserId(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(StrategyResponse(success = false, message = "인증이 필요합니다"))

        return try {
            val response = strategyService.updateStrategy(id, userId, request)
            ResponseEntity.ok(response)
        } catch (e: StrategyException) {
            ResponseEntity.badRequest().body(
                StrategyResponse(success = false, message = e.message)
            )
        }
    }

    /**
     * 전략 삭제
     * DELETE /api/v1/strategies/{id}
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "전략 삭제",
        description = "전략을 삭제합니다. 소유자만 삭제 가능하며, 구독자가 있는 전략은 삭제할 수 없습니다."
    )
    fun deleteStrategy(
        @Parameter(description = "전략 ID")
        @PathVariable id: Long,
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<StrategyResponse> {
        val userId = extractUserId(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(StrategyResponse(success = false, message = "인증이 필요합니다"))

        return try {
            val response = strategyService.deleteStrategy(id, userId)
            ResponseEntity.ok(response)
        } catch (e: StrategyException) {
            ResponseEntity.badRequest().body(
                StrategyResponse(success = false, message = e.message)
            )
        }
    }

    /**
     * 내 전략 목록 조회
     * GET /api/v1/strategies/me
     */
    @GetMapping("/me")
    @Operation(
        summary = "내 전략 목록 조회",
        description = "로그인한 사용자의 전략 목록을 조회합니다."
    )
    fun getMyStrategies(
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<MyStrategiesResponse> {
        val userId = extractUserId(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        return try {
            val response = strategyService.getMyStrategies(userId)
            ResponseEntity.ok(response)
        } catch (e: StrategyException) {
            ResponseEntity.badRequest().build()
        }
    }

    /**
     * Authorization 헤더에서 userId 추출
     */
    private fun extractUserId(authorization: String): String? {
        if (!authorization.startsWith("Bearer ")) {
            return null
        }

        val token = authorization.removePrefix("Bearer ")
        val loginResponse = authService.validateToken(token) ?: return null

        return loginResponse.user?.userId
    }
}
