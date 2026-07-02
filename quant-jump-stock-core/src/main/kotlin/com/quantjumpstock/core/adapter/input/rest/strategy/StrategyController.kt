package com.quantjumpstock.core.adapter.input.rest.strategy

import com.quantjumpstock.core.application.strategy.CreateStrategyRequest
import com.quantjumpstock.core.application.strategy.MyStrategiesResponse
import com.quantjumpstock.core.application.strategy.StrategyDetailResponse
import com.quantjumpstock.core.application.strategy.StrategyResponse
import com.quantjumpstock.core.application.strategy.StrategyService
import com.quantjumpstock.core.application.strategy.UpdateStrategyRequest
import com.quantjumpstock.core.infrastructure.security.CurrentUser
import com.quantjumpstock.core.infrastructure.security.UnauthorizedException
import com.quantjumpstock.core.infrastructure.security.UserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Strategy Controller
 * 전략 CRUD API
 *
 * 인증은 JwtAuthenticationFilter 가 채운 principal(@CurrentUser)을 사용하고,
 * 도메인 예외 → HTTP 매핑은 GlobalExceptionHandler 가 담당한다.
 */
@RestController
@RequestMapping("/api/v1/strategies")
@Tag(name = "Strategy", description = "전략 관리 API")
class StrategyController(
    private val strategyService: StrategyService
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
        @CurrentUser user: UserPrincipal?,
        @RequestBody request: CreateStrategyRequest
    ): ResponseEntity<StrategyResponse> {
        val userId = requireUserId(user)
        val response = strategyService.createStrategy(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
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
        @CurrentUser user: UserPrincipal?
    ): ResponseEntity<StrategyDetailResponse> {
        val response = strategyService.getStrategy(id, user?.userId)
        return ResponseEntity.ok(response)
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
        @CurrentUser user: UserPrincipal?,
        @RequestBody request: UpdateStrategyRequest
    ): ResponseEntity<StrategyResponse> {
        val userId = requireUserId(user)
        val response = strategyService.updateStrategy(id, userId, request)
        return ResponseEntity.ok(response)
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
        @CurrentUser user: UserPrincipal?
    ): ResponseEntity<StrategyResponse> {
        val userId = requireUserId(user)
        val response = strategyService.deleteStrategy(id, userId)
        return ResponseEntity.ok(response)
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
        @CurrentUser user: UserPrincipal?
    ): ResponseEntity<MyStrategiesResponse> {
        val userId = requireUserId(user)
        val response = strategyService.getMyStrategies(userId)
        return ResponseEntity.ok(response)
    }

    private fun requireUserId(user: UserPrincipal?): String =
        user?.userId ?: throw UnauthorizedException("인증이 필요합니다")
}
