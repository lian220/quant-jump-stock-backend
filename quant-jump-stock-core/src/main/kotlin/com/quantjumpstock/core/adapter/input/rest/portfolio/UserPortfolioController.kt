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
@RequestMapping("/api/v1/portfolios")
@Tag(name = "User Portfolio", description = "사용자 포트폴리오 관리 API")
class UserPortfolioController(
    private val userPortfolioService: UserPortfolioService
) {

    @GetMapping
    @Operation(summary = "내 포트폴리오 목록", description = "로그인한 사용자의 포트폴리오 목록을 조회합니다.")
    fun getUserPortfolios(
        @CurrentUser user: UserPrincipal?
    ): ResponseEntity<List<UserPortfolioResponse>> {
        val userId = requireUserDbId(user)
        val response = userPortfolioService.getUserPortfolios(userId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{id}/stocks")
    @Operation(summary = "포트폴리오 종목 목록", description = "포트폴리오의 종목 목록을 조회합니다.")
    fun getPortfolioStocks(
        @Parameter(description = "포트폴리오 ID") @PathVariable id: Long,
        @CurrentUser user: UserPrincipal?
    ): ResponseEntity<PortfolioStockListResponse> {
        val userId = requireUserDbId(user)
        val response = userPortfolioService.getPortfolioStocks(id, userId)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{id}/stocks")
    @Operation(summary = "포트폴리오 종목 추가", description = "포트폴리오에 종목을 추가합니다.")
    fun addPortfolioStock(
        @Parameter(description = "포트폴리오 ID") @PathVariable id: Long,
        @CurrentUser user: UserPrincipal?,
        @RequestBody request: AddPortfolioStockRequest
    ): ResponseEntity<Any> {
        val userId = requireUserDbId(user)
        val response = userPortfolioService.addPortfolioStock(id, request, userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PutMapping("/{id}/stocks/{stockId}")
    @Operation(summary = "포트폴리오 종목 비중 수정", description = "포트폴리오 종목의 비중을 수정합니다.")
    fun updatePortfolioStock(
        @Parameter(description = "포트폴리오 ID") @PathVariable id: Long,
        @Parameter(description = "종목 ID") @PathVariable stockId: Long,
        @CurrentUser user: UserPrincipal?,
        @RequestBody request: UpdatePortfolioStockRequest
    ): ResponseEntity<Any> {
        val userId = requireUserDbId(user)
        val response = userPortfolioService.updatePortfolioStock(id, stockId, request, userId)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{id}/stocks/{stockId}")
    @Operation(summary = "포트폴리오 종목 삭제", description = "포트폴리오에서 종목을 삭제합니다.")
    fun removePortfolioStock(
        @Parameter(description = "포트폴리오 ID") @PathVariable id: Long,
        @Parameter(description = "종목 ID") @PathVariable stockId: Long,
        @CurrentUser user: UserPrincipal?
    ): ResponseEntity<PortfolioResponse> {
        val userId = requireUserDbId(user)
        val response = userPortfolioService.removePortfolioStock(id, stockId, userId)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/from-strategy/{strategyId}")
    @Operation(summary = "전략 기반 포트폴리오 생성", description = "전략의 기본 종목을 복사하여 사용자 포트폴리오를 생성합니다.")
    fun createFromStrategy(
        @Parameter(description = "전략 ID") @PathVariable strategyId: Long,
        @CurrentUser user: UserPrincipal?
    ): ResponseEntity<Any> {
        val userId = requireUserDbId(user)
        val response = userPortfolioService.createFromStrategy(userId, strategyId)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * 인증된 사용자의 DB PK.
     * 모든 액세스 토큰 발급 경로가 dbId claim 을 필수로 넣으므로(JwtService) principal.id 를 신뢰한다.
     */
    private fun requireUserDbId(user: UserPrincipal?): Long =
        user?.id?.takeIf { it > 0 } ?: throw UnauthorizedException("인증이 필요합니다")
}
