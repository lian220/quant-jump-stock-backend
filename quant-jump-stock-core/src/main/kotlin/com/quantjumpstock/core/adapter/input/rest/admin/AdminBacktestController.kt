package com.quantjumpstock.core.adapter.input.rest.admin

import com.quantjumpstock.core.application.backtest.AdminBacktestService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

/**
 * Admin 백테스트 일괄 실행 API
 * 어드민에서 커스텀 기간으로 전체/특정 전략의 백테스트를 즉시 실행합니다.
 */
@RestController
@RequestMapping("/api/v1/admin/backtest")
@Tag(name = "Admin Backtest", description = "관리자용 백테스트 일괄 실행 API")
@PreAuthorize("hasRole('ADMIN')")
class AdminBacktestController(
    private val adminBacktestService: AdminBacktestService
) {

    /**
     * PUBLISHED 전략 전체 백테스트 실행
     * POST /api/v1/admin/backtest/run-all
     */
    @PostMapping("/run-all")
    @Operation(
        summary = "전체 전략 백테스트 실행",
        description = "PUBLISHED 상태인 모든 전략에 대해 커스텀 기간으로 백테스트를 실행합니다."
    )
    fun runAllBacktests(
        @RequestBody(required = false) request: AdminBacktestRequest?
    ): ResponseEntity<Any> {
        return try {
            val result = adminBacktestService.runAllBacktests(
                periodDays = request?.periodDays ?: AdminBacktestService.DEFAULT_PERIOD_DAYS,
                benchmark = request?.benchmark ?: AdminBacktestService.DEFAULT_BENCHMARK,
                initialCapital = request?.initialCapital ?: AdminBacktestService.DEFAULT_INITIAL_CAPITAL
            )
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                mapOf("error" to "ADMIN_BACKTEST_FAILED", "message" to (e.message ?: "일괄 백테스트 실행 중 오류"))
            )
        }
    }

    /**
     * 특정 전략 백테스트 실행
     * POST /api/v1/admin/backtest/run/{strategyId}
     */
    @PostMapping("/run/{strategyId}")
    @Operation(
        summary = "특정 전략 백테스트 실행",
        description = "지정된 전략에 대해 커스텀 기간으로 백테스트를 실행합니다."
    )
    fun runBacktest(
        @Parameter(description = "전략 ID") @PathVariable strategyId: Long,
        @RequestBody(required = false) request: AdminBacktestRequest?
    ): ResponseEntity<Any> {
        return try {
            val result = adminBacktestService.runBacktest(
                strategyId = strategyId,
                periodDays = request?.periodDays ?: AdminBacktestService.DEFAULT_PERIOD_DAYS,
                benchmark = request?.benchmark ?: AdminBacktestService.DEFAULT_BENCHMARK,
                initialCapital = request?.initialCapital ?: AdminBacktestService.DEFAULT_INITIAL_CAPITAL
            )
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                mapOf("error" to "STRATEGY_NOT_FOUND", "message" to e.message)
            )
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(
                mapOf("error" to "NO_TICKERS", "message" to e.message)
            )
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                mapOf("error" to "ADMIN_BACKTEST_FAILED", "message" to (e.message ?: "백테스트 실행 중 오류"))
            )
        }
    }
}

data class AdminBacktestRequest(
    val periodDays: Long? = null,
    val benchmark: String? = null,
    val initialCapital: BigDecimal? = null
)
