package com.quantjumpstock.core.adapter.input.rest.backtest

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.backtest.*
import com.quantjumpstock.core.application.portfolio.StrategyDefaultStockService
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.domain.port.output.Benchmark
import com.quantjumpstock.core.domain.port.output.StockRepository
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
    private val authService: AuthService,
    private val userTierService: UserTierService,
    private val userRepository: UserRepository,
    private val defaultStockService: StrategyDefaultStockService,
    private val stockRepository: StockRepository
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
    ): ResponseEntity<Any> {
        val userLoginId = authorization?.let { extractUserId(it) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        // 문자열 userId → DB PK(Long) 변환하여 Kafka에 숫자 ID로 전달
        val userDbId = userRepository.findByUserId(userLoginId)?.id
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "USER_NOT_FOUND", "message" to "사용자를 찾을 수 없습니다."))
        val userId = userDbId.toString()

        // tickers가 비어있으면 전략 기본 종목 → stocks 테이블 전체 순서로 fallback
        val effectiveTickers = if (request.tickers.isEmpty()) {
            val fromDefault = try {
                val defaultStocks = defaultStockService.getDefaultStocks(request.strategyId)
                defaultStocks.stocks.mapNotNull { it.ticker }
            } catch (e: Exception) {
                emptyList()
            }

            fromDefault.ifEmpty {
                // 기본 종목 없으면 stocks 테이블 전체 종목 사용
                stockRepository.findAll().mapNotNull { it.ticker }
            }
        } else {
            request.tickers
        }

        if (effectiveTickers.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(mapOf(
                    "error" to "TICKERS_REQUIRED",
                    "message" to "백테스트 대상 종목이 없습니다. stocks 테이블에 종목을 등록해주세요."
                ))
        }

        val effectiveRequest = request.copy(tickers = effectiveTickers)

        // 벤치마크 검증
        if (!Benchmark.existsByTicker(effectiveRequest.benchmark)) {
            return ResponseEntity.badRequest()
                .body(mapOf(
                    "error" to "INVALID_BENCHMARK",
                    "message" to "지원하지 않는 벤치마크입니다: ${effectiveRequest.benchmark}",
                    "availableBenchmarks" to "/api/v1/backtest/benchmarks"
                ))
        }

        // Rate Limit 원자적 체크 + 카운트 증가 (TOCTOU 방지)
        val limitResult = userTierService.checkAndIncrementBacktestCount(userLoginId)
        if (!limitResult.allowed) {
            val rateLimitResponse = BacktestRateLimitResponse(
                dailyLimit = limitResult.dailyLimit,
                remaining = limitResult.remaining,
                tier = limitResult.tier,
                message = limitResult.message ?: "일일 백테스트 한도를 초과했습니다."
            )
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "86400")
                .body(rateLimitResponse)
        }

        return try {
            val response = backtestService.runBacktest(effectiveRequest, userId)
            ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf(
                    "error" to "BACKTEST_SUBMISSION_FAILED",
                    "message" to "백테스트 요청 처리 중 오류가 발생했습니다."
                ))
        }
    }

    /**
     * 사용 가능한 벤치마크 목록 조회
     * GET /api/v1/backtest/benchmarks
     */
    @GetMapping("/benchmarks")
    @Operation(
        summary = "벤치마크 목록 조회",
        description = "백테스트에 사용할 수 있는 벤치마크 목록을 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "벤치마크 목록 조회 성공")
    fun getBenchmarks(): ResponseEntity<List<BenchmarkResponse>> {
        val benchmarks = Benchmark.getAll()
        val response = benchmarks.map { BenchmarkResponse(it.ticker, it.name, it.type) }
        return ResponseEntity.ok(response)
    }

    /**
     * 초보자용 Enhanced 백테스트 결과 조회
     * GET /api/v1/backtest/{id}/enhanced
     * SCRUM-245: 신호등 시스템 + 평문 설명 + 용어 사전
     */
    @GetMapping("/{id}/enhanced")
    @Operation(
        summary = "초보자용 백테스트 결과 조회",
        description = "백테스트 결과를 초보자 친화적으로 조회합니다. 성과 등급(신호등), 평문 요약, 용어 설명을 포함합니다."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Enhanced 백테스트 결과 조회 성공",
            content = [Content(schema = Schema(implementation = BacktestEnhancedResponse::class))]
        ),
        ApiResponse(responseCode = "202", description = "아직 처리 중"),
        ApiResponse(responseCode = "404", description = "백테스트 결과를 찾을 수 없음")
    )
    fun getEnhancedBacktestResult(
        @Parameter(description = "백테스트 ID (DB ID 또는 requestId)") @PathVariable id: String
    ): ResponseEntity<Any> {
        return try {
            val resolvedId = backtestService.resolveBacktestId(id)
            val status = backtestService.getBacktestStatus(resolvedId)

            when (status) {
                "RUNNING" -> {
                    val pendingResponse = BacktestPendingResponse(
                        id = resolvedId,
                        status = "RUNNING",
                        message = "백테스트가 아직 처리 중입니다.",
                        estimatedRemainingTime = 30
                    )
                    ResponseEntity.status(HttpStatus.ACCEPTED)
                        .header("Retry-After", "10")
                        .body(pendingResponse)
                }
                "FAILED" -> {
                    val result = backtestService.getBacktestResult(resolvedId)
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(result)
                }
                else -> {
                    val enhanced = backtestService.getEnhancedBacktestResult(resolvedId)
                    ResponseEntity.ok(enhanced)
                }
            }
        } catch (e: BacktestNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to e.message))
        }
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
        @Parameter(description = "백테스트 ID (DB ID 또는 requestId)") @PathVariable id: String
    ): ResponseEntity<Any> {
        return try {
            // DB ID (Long) 또는 requestId (UUID) 모두 지원
            val resolvedId = backtestService.resolveBacktestId(id)
            val status = backtestService.getBacktestStatus(resolvedId)

            when (status) {
                "RUNNING" -> {
                    val pendingResponse = BacktestPendingResponse(
                        id = resolvedId,
                        status = "RUNNING",
                        message = "백테스트가 아직 처리 중입니다.",
                        estimatedRemainingTime = 30
                    )
                    ResponseEntity.status(HttpStatus.ACCEPTED)
                        .header("Retry-After", "10")
                        .body(pendingResponse)
                }
                "FAILED" -> {
                    val result = backtestService.getBacktestResult(resolvedId)
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(result)
                }
                else -> {
                    val result = backtestService.getBacktestResult(resolvedId)
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
