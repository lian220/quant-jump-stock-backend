package com.quantjumpstock.core.adapter.input.rest.backtest

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.backtest.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Backtest Controller
 * 백테스트 API
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
     * 백테스트 실행
     * POST /api/v1/backtest/run
     */
    @PostMapping("/run")
    @Operation(
        summary = "백테스트 실행",
        description = "전략에 대한 백테스트를 실행합니다. 요청은 Kafka를 통해 비동기로 처리됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "백테스트 요청 접수됨"),
        ApiResponse(responseCode = "400", description = "잘못된 날짜 범위 또는 파라미터"),
        ApiResponse(responseCode = "401", description = "인증 필요"),
        ApiResponse(responseCode = "403", description = "전략 접근 권한 없음"),
        ApiResponse(responseCode = "404", description = "전략을 찾을 수 없음"),
        ApiResponse(responseCode = "429", description = "Rate Limit 초과")
    )
    fun runBacktest(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: BacktestRunRequest
    ): ResponseEntity<BacktestRunResponse> {
        val userId = authorization?.let { extractUserIdAsLong(it) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val response = backtestService.runBacktest(request, userId.toString())

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
    }

    /**
     * 백테스트 결과 조회
     * GET /api/v1/backtest/{id}
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "백테스트 결과 조회",
        description = "백테스트 결과를 조회합니다. 성과 지표, 수익 곡선, 거래 내역을 포함합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "백테스트 결과 조회 성공"),
        ApiResponse(responseCode = "202", description = "아직 처리 중 (Retry-After 헤더 포함)"),
        ApiResponse(responseCode = "404", description = "백테스트 결과를 찾을 수 없음")
    )
    fun getBacktestResult(
        @Parameter(description = "백테스트 ID") @PathVariable id: Long
    ): ResponseEntity<Any> {
        return try {
            val status = backtestService.getBacktestStatus(id)

            when (status) {
                "RUNNING" -> {
                    val pendingResponse = BacktestPendingResponse(
                        id = id,
                        status = "RUNNING",
                        message = "백테스트가 아직 처리 중입니다.",
                        estimatedRemainingTime = 30
                    )
                    ResponseEntity.status(HttpStatus.ACCEPTED)
                        .header("Retry-After", "10")
                        .body(pendingResponse)
                }
                "FAILED" -> {
                    val result = backtestService.getBacktestResult(id)
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(result)
                }
                else -> {
                    val result = backtestService.getBacktestResult(id)
                    ResponseEntity.ok(result)
                }
            }
        } catch (e: BacktestNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to e.message))
        }
    }

    /**
     * 백테스트 목록 조회
     * GET /api/v1/backtest
     */
    @GetMapping
    @Operation(
        summary = "백테스트 목록 조회",
        description = "백테스트 목록을 페이징하여 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "백테스트 목록 조회 성공")
    fun getBacktestList(
        @Parameter(description = "전략 ID (선택)") @RequestParam(required = false) strategyId: Long?,
        @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(defaultValue = "20") size: Int,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<PagedResponse<BacktestListItemResponse>> {
        val userId = authorization?.let { extractUserIdAsLong(it) }
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)

        val response = backtestService.getBacktestList(strategyId, userId, safePage, safeSize)

        return ResponseEntity.ok(response)
    }

    // ============================================================================
    // Legacy Endpoint (하위 호환성)
    // ============================================================================

    /**
     * 백테스트 요청 (Legacy)
     * POST /api/v1/backtest/request
     * @deprecated Use POST /api/v1/backtest/run instead
     */
    @Deprecated("Use POST /api/v1/backtest/run instead")
    @PostMapping("/request")
    @Operation(
        summary = "백테스트 요청 (Legacy)",
        description = "전략에 대한 백테스트를 요청합니다. 이 엔드포인트는 deprecated 되었습니다. /run을 사용하세요."
    )
    fun requestBacktest(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: BacktestRequestDto
    ): ResponseEntity<BacktestResponse> {
        val userId = authorization?.let { extractUserId(it) }

        @Suppress("DEPRECATION")
        val response = backtestService.requestBacktest(request, userId)

        return if (response.success) {
            ResponseEntity.accepted().body(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }

    // ============================================================================
    // Private Methods
    // ============================================================================

    /**
     * Authorization 헤더에서 userId 추출 (String)
     */
    private fun extractUserId(authorization: String): String? {
        if (!authorization.startsWith("Bearer ")) {
            return null
        }

        val token = authorization.removePrefix("Bearer ")
        val loginResponse = authService.validateToken(token) ?: return null

        return loginResponse.user?.userId
    }

    /**
     * Authorization 헤더에서 userId 추출 (Long)
     */
    private fun extractUserIdAsLong(authorization: String): Long? {
        return extractUserId(authorization)?.toLongOrNull()
    }
}
