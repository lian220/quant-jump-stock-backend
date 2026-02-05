package com.quantjumpstock.core.adapter.input.rest.backtest

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.backtest.BacktestRequestDto
import com.quantjumpstock.core.application.backtest.BacktestResponse
import com.quantjumpstock.core.application.backtest.BacktestService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Backtest Controller
 * 백테스트 요청 API
 */
@RestController
@RequestMapping("/api/v1/backtest")
@Tag(name = "Backtest", description = "백테스트 API")
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:4000"], allowCredentials = "true")
class BacktestController(
    private val backtestService: BacktestService,
    private val authService: AuthService
) {

    /**
     * 백테스트 요청
     * POST /api/v1/backtest/request
     */
    @PostMapping("/request")
    @Operation(
        summary = "백테스트 요청",
        description = "전략에 대한 백테스트를 요청합니다. 요청은 Kafka를 통해 비동기로 처리됩니다."
    )
    fun requestBacktest(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: BacktestRequestDto
    ): ResponseEntity<BacktestResponse> {
        val userId = authorization?.let { extractUserId(it) }

        val response = backtestService.requestBacktest(request, userId)

        return if (response.success) {
            ResponseEntity.accepted().body(response)
        } else {
            ResponseEntity.badRequest().body(response)
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
