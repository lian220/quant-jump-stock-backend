package com.quantjumpstock.core.adapter.input.rest.admin

import com.quantjumpstock.core.application.backtest.BacktestCleanupService
import com.quantjumpstock.core.application.backtest.CanonicalBacktestService
import com.quantjumpstock.core.application.trading.AutoTradingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 스케줄러 수동 트리거 Controller (Admin 전용)
 * 관리자가 수동으로 스케줄 잡을 실행할 수 있는 엔드포인트입니다.
 */
@Tag(name = "Admin - Scheduler", description = "스케줄러 수동 트리거 API")
@RestController
@RequestMapping("/api/v1/admin/scheduler")
class AdminSchedulerController(
    private val autoTradingService: AutoTradingService,
    private val canonicalBacktestService: CanonicalBacktestService,
    private val backtestCleanupService: BacktestCleanupService,
    private val cacheManager: CacheManager
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Operation(summary = "자동 매수 수동 실행")
    @PostMapping("/trigger/auto-buy")
    fun triggerAutoBuy(): ResponseEntity<Map<String, Any>> {
        logger.info("[Admin] 자동 매수 수동 트리거")
        return try {
            autoTradingService.executeAutoTrading()
            ResponseEntity.ok(mapOf("success" to true, "message" to "자동 매수가 실행되었습니다."))
        } catch (e: Exception) {
            logger.error("[Admin] 자동 매수 실행 중 오류", e)
            ResponseEntity.internalServerError()
                .body(mapOf("success" to false, "message" to "자동 매수 실행 중 오류가 발생했습니다."))
        }
    }

    @Operation(summary = "Canonical 백테스트 갱신 수동 실행")
    @PostMapping("/trigger/canonical-backtest")
    fun triggerCanonicalBacktest(): ResponseEntity<Map<String, Any>> {
        logger.info("[Admin] Canonical 백테스트 갱신 수동 트리거")
        return try {
            canonicalBacktestService.refreshAllCanonicalBacktests()

            listOf("marketplaceStrategies", "strategyDetail").forEach { cacheName ->
                cacheManager.getCache(cacheName)?.let {
                    it.clear()
                    logger.info("$cacheName 캐시 초기화 완료")
                }
            }

            ResponseEntity.ok(mapOf("success" to true, "message" to "Canonical 백테스트 갱신이 완료되었습니다."))
        } catch (e: Exception) {
            logger.error("[Admin] Canonical 백테스트 실행 중 오류", e)
            ResponseEntity.internalServerError()
                .body(mapOf("success" to false, "message" to "Canonical 백테스트 실행 중 오류가 발생했습니다."))
        }
    }

    @Operation(summary = "백테스트 데이터 정리 수동 실행")
    @PostMapping("/trigger/backtest-cleanup")
    fun triggerBacktestCleanup(): ResponseEntity<Map<String, Any>> {
        logger.info("[Admin] 백테스트 데이터 정리 수동 트리거")
        return try {
            backtestCleanupService.runCleanup()
            ResponseEntity.ok(mapOf("success" to true, "message" to "백테스트 데이터 정리가 완료되었습니다."))
        } catch (e: Exception) {
            logger.error("[Admin] 백테스트 데이터 정리 실행 중 오류", e)
            ResponseEntity.internalServerError()
                .body(mapOf("success" to false, "message" to "백테스트 데이터 정리 실행 중 오류가 발생했습니다."))
        }
    }
}
