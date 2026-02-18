package com.quantjumpstock.core.adapter.input.rest.backtest

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.backtest.*
import com.quantjumpstock.core.application.portfolio.StrategyDefaultStockService
import com.quantjumpstock.core.domain.model.backtest.BacktestStatus
import com.quantjumpstock.core.domain.model.backtest.UniverseType
import com.quantjumpstock.core.domain.port.output.BacktestResultRepository
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.domain.port.output.Benchmark
import com.quantjumpstock.core.domain.port.output.StockRepository
import com.quantjumpstock.core.domain.port.output.StrategyRepository
import org.slf4j.LoggerFactory
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Backtest Controller
 * 백테스트 API
 */
@RestController
@RequestMapping("/api/v1/backtest")
@Tag(name = "Backtest", description = "백테스트 API")
@ImportRuntimeHints(BacktestRuntimeHints::class)
class BacktestController(
    private val backtestService: BacktestService,
    private val authService: AuthService,
    private val userTierService: UserTierService,
    private val userRepository: UserRepository,
    private val defaultStockService: StrategyDefaultStockService,
    private val stockRepository: StockRepository,
    private val strategyRepository: StrategyRepository,
    private val backtestResultRepository: BacktestResultRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

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

        // 백테스트 기간 검증 (최대 1년)
        val periodValidation = validateBacktestPeriod(request.startDate, request.endDate)
        if (periodValidation != null) {
            return ResponseEntity.badRequest().body(periodValidation)
        }

        // 전략 존재 여부 검증 (단일 조회로 TOCTOU 방지)
        val strategy = strategyRepository.findById(request.strategyId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf(
                    "error" to "STRATEGY_NOT_FOUND",
                    "message" to "전략을 찾을 수 없습니다: ${request.strategyId}"
                ))

        // 문자열 userId → DB PK(Long) 변환하여 Kafka에 숫자 ID로 전달
        val userDbId = userRepository.findByUserId(userLoginId)?.id
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "USER_NOT_FOUND", "message" to "사용자를 찾을 수 없습니다."))
        val userId = userDbId.toString()
        val universeType = request.universeType?.let {
            try { UniverseType.valueOf(it) } catch (e: Exception) { strategy.recommendedUniverseType }
        } ?: strategy.recommendedUniverseType

        // SCRUM-344: 티어별 백테스트 보관 제한 (무료 5개/전략, 프리미엄 무제한)
        val tierInfo = userTierService.checkBacktestLimit(userLoginId)
        if (tierInfo.tier == "FREE") {
            val customCount = backtestResultRepository.countUserCustomByUserIdAndStrategyId(userDbId, request.strategyId)
            if (customCount >= FREE_BACKTEST_LIMIT_PER_STRATEGY) {
                return ResponseEntity.badRequest()
                    .body(mapOf(
                        "error" to "BACKTEST_LIMIT_EXCEEDED",
                        "message" to "무료 사용자는 전략당 최대 ${FREE_BACKTEST_LIMIT_PER_STRATEGY}개의 커스텀 백테스트만 보관할 수 있습니다. 현재: ${customCount}개",
                        "currentCount" to customCount,
                        "limit" to FREE_BACKTEST_LIMIT_PER_STRATEGY,
                        "tier" to "FREE"
                    ))
            }
        }

        // SCRUM-344: 유니버스 타입에 따른 종목 결정
        val effectiveTickers = resolveTickersByUniverse(universeType, request.strategyId, request.tickers)

        if (effectiveTickers.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(mapOf(
                    "error" to "TICKERS_REQUIRED",
                    "message" to "백테스트 대상 종목이 없습니다. stocks 테이블에 종목을 등록해주세요."
                ))
        }

        val effectiveRequest = request.copy(tickers = effectiveTickers, universeType = universeType.name)

        // SCRUM-337: 다중 벤치마크 검증
        val effectiveBenchmarks = effectiveRequest.effectiveBenchmarks()

        // 최대 개수 검증
        if (effectiveBenchmarks.size > Benchmark.MAX_BENCHMARKS) {
            return ResponseEntity.badRequest()
                .body(mapOf(
                    "error" to "TOO_MANY_BENCHMARKS",
                    "message" to "벤치마크는 최대 ${Benchmark.MAX_BENCHMARKS}개까지 설정할 수 있습니다. 현재: ${effectiveBenchmarks.size}개"
                ))
        }

        // 각 벤치마크 유효성 검증
        for (bm in effectiveBenchmarks) {
            if (!Benchmark.isValidBenchmark(bm)) {
                return ResponseEntity.badRequest()
                    .body(mapOf(
                        "error" to "INVALID_BENCHMARK",
                        "message" to "지원하지 않는 벤치마크입니다: $bm",
                        "availableBenchmarks" to "/api/v1/backtest/benchmarks"
                    ))
            }

            // STRATEGY:ID 형태인 경우 해당 전략 존재 여부 확인
            val strategyBmId = Benchmark.parseStrategyId(bm)
            if (strategyBmId != null && !strategyRepository.existsById(strategyBmId)) {
                return ResponseEntity.badRequest()
                    .body(mapOf(
                        "error" to "STRATEGY_BENCHMARK_NOT_FOUND",
                        "message" to "벤치마크로 지정한 전략을 찾을 수 없습니다: $bm"
                    ))
            }
        }

        // 중복 실행 방지: 동일 설정(벤치마크+자본금)의 기존 백테스트 확인
        val effectiveBenchmark = effectiveBenchmarks.first()
        val existingList = backtestResultRepository.findUserCustomBySettings(
            userDbId, request.strategyId, effectiveBenchmark, request.initialCapital
        )
        val existing = existingList.firstOrNull()

        if (existing != null) {
            val reqStart = LocalDate.parse(request.startDate)
            val reqEnd = LocalDate.parse(request.endDate)

            when {
                // RUNNING 상태 → 이미 실행 중
                existing.status == BacktestStatus.RUNNING -> {
                    logger.info("중복 방지: RUNNING 상태 백테스트 존재. userId={}, strategyId={}, existingId={}", userDbId, request.strategyId, existing.id)
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(mapOf(
                            "error" to "BACKTEST_ALREADY_RUNNING",
                            "message" to "동일한 설정으로 이미 실행 중인 백테스트가 있습니다.",
                            "existingBacktestId" to existing.id,
                            "existingRequestId" to existing.requestId
                        ))
                }

                // 기존 기간이 요청 기간을 포함 (완전 동일 포함)
                existing.startDate <= reqStart && existing.endDate >= reqEnd -> {
                    logger.info("중복 방지: 기존 결과 반환. userId={}, strategyId={}, existingId={}", userDbId, request.strategyId, existing.id)
                    return ResponseEntity.ok(BacktestRunResponse(
                        backtestId = existing.requestId ?: existing.id.toString(),
                        status = "COMPLETED",
                        estimatedTime = 0,
                        message = "동일한 설정의 기존 백테스트 결과가 있습니다."
                    ))
                }

                // 과거 확장 (startDate가 앞당겨짐) → 기존 삭제 후 재실행
                reqStart < existing.startDate -> {
                    logger.info("중복 방지: 과거 확장 → 기존 삭제 후 재실행. existingId={}, existing={}~{}, req={}~{}",
                        existing.id, existing.startDate, existing.endDate, reqStart, reqEnd)
                    existing.id?.let { backtestResultRepository.deleteById(it) }
                    // 아래 신규 실행 로직으로 진행
                }

                // 미래 확장 (endDate만 늘어남, startDate 동일) → 기존 삭제 후 재실행
                reqEnd > existing.endDate && reqStart == existing.startDate -> {
                    logger.info("중복 방지: 미래 확장 → 기존 삭제 후 재실행. existingId={}, existing={}~{}, req={}~{}",
                        existing.id, existing.startDate, existing.endDate, reqStart, reqEnd)
                    existing.id?.let { backtestResultRepository.deleteById(it) }
                    // 아래 신규 실행 로직으로 진행
                }
            }
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
        // 문자열 loginId → DB PK(Long) 변환하여 유저 격리 필터링
        val userLoginId = authorization?.let { extractUserId(it) }
        val userId = userLoginId?.let { userRepository.findByUserId(it)?.id }
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)

        val response = backtestService.getBacktestList(strategyId, userId, safePage, safeSize)

        return ResponseEntity.ok(response)
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

    /**
     * 백테스트 기간 검증 (최대 365일)
     * @return 검증 실패 시 에러 맵, 성공 시 null
     */
    private fun validateBacktestPeriod(startDate: String, endDate: String): Map<String, Any>? {
        val start: LocalDate
        val end: LocalDate

        try {
            start = LocalDate.parse(startDate)
            end = LocalDate.parse(endDate)
        } catch (e: DateTimeParseException) {
            return mapOf(
                "error" to "INVALID_DATE_FORMAT",
                "message" to "올바른 날짜 형식이 아닙니다. (yyyy-MM-dd)"
            )
        }

        if (end.isBefore(start)) {
            return mapOf(
                "error" to "INVALID_DATE_RANGE",
                "message" to "종료일은 시작일 이후여야 합니다."
            )
        }

        val diffDays = ChronoUnit.DAYS.between(start, end)
        if (diffDays > MAX_BACKTEST_DAYS) {
            return mapOf(
                "error" to "BACKTEST_PERIOD_EXCEEDED",
                "message" to "백테스트 기간은 최대 1년(${MAX_BACKTEST_DAYS}일)까지 가능합니다. 현재: ${diffDays}일"
            )
        }

        return null
    }

    /**
     * SCRUM-344: 유니버스 타입에 따른 종목 결정
     */
    private fun resolveTickersByUniverse(
        universeType: UniverseType,
        strategyId: Long,
        requestTickers: List<String>
    ): List<String> {
        return when (universeType) {
            UniverseType.MARKET -> stockRepository.findAll().mapNotNull { it.ticker }
            UniverseType.PORTFOLIO -> {
                val fromDefault = try {
                    defaultStockService.getDefaultStocks(strategyId).stocks.mapNotNull { it.ticker }
                } catch (e: Exception) {
                    emptyList()
                }
                fromDefault.ifEmpty { stockRepository.findAll().mapNotNull { it.ticker } }
            }
            UniverseType.FIXED -> {
                requestTickers.ifEmpty {
                    val fromDefault = try {
                        defaultStockService.getDefaultStocks(strategyId).stocks.mapNotNull { it.ticker }
                    } catch (e: Exception) {
                        emptyList()
                    }
                    fromDefault.ifEmpty { stockRepository.findAll().mapNotNull { it.ticker } }
                }
            }
            UniverseType.SECTOR -> stockRepository.findAll().mapNotNull { it.ticker }
        }
    }

    companion object {
        private const val MAX_BACKTEST_DAYS = 365L
        private const val FREE_BACKTEST_LIMIT_PER_STRATEGY = 5L
    }
}
