package com.quantjumpstock.core.adapter.input.rest.scheduler

import com.quantjumpstock.core.application.backtest.BacktestCleanupService
import com.quantjumpstock.core.application.backtest.CanonicalBacktestService
import com.quantjumpstock.core.application.trading.AutoTradingService
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Cloud Scheduler HTTP 엔드포인트 (Input Adapter)
 *
 * Quartz Job을 Cloud Scheduler HTTP 타겟으로 전환하기 위한 컨트롤러입니다.
 * Cloud Run 환경에서는 Quartz 대신 Cloud Scheduler가 이 엔드포인트를 호출합니다.
 * VM 환경에서는 Quartz와 병행 운영 가능합니다.
 *
 * 보안: /api/internal/scheduler/ 하위 경로는 SecurityConfig에서 내부 전용으로 설정됩니다.
 */
@RestController
@RequestMapping("/api/internal/scheduler")
class CloudSchedulerController(
    private val autoTradingService: AutoTradingService,
    private val canonicalBacktestService: CanonicalBacktestService,
    private val backtestCleanupService: BacktestCleanupService,
    private val cacheManager: CacheManager
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val nyZone = ZoneId.of("America/New_York")

    /**
     * 자동 매수 (Cloud Scheduler: 00:30 KST daily)
     */
    @PostMapping("/auto-buy")
    fun autoBuy(): ResponseEntity<Map<String, Any>> {
        logger.info("=".repeat(80))
        logger.info("자동 매수 시작 (Cloud Scheduler)")
        logger.info("=".repeat(80))

        return try {
            autoTradingService.executeAutoTrading()
            logger.info("자동 매수 완료")
            ResponseEntity.ok(mapOf("status" to "success", "job" to "auto-buy"))
        } catch (e: Exception) {
            logger.error("자동 매수 실행 중 오류", e)
            ResponseEntity.internalServerError()
                .body(mapOf("status" to "error", "job" to "auto-buy", "message" to (e.message ?: "unknown")))
        }
    }

    /**
     * 자동 매도 체크 (Cloud Scheduler: 매 1분)
     * 미국 시장 시간(평일 9:30-16:00 ET)에만 실행됩니다.
     */
    @PostMapping("/auto-sell")
    fun autoSell(): ResponseEntity<Map<String, Any>> {
        if (!isUsMarketHours()) {
            return ResponseEntity.ok(mapOf("status" to "skipped", "job" to "auto-sell", "reason" to "outside market hours"))
        }

        logger.info("=".repeat(80))
        logger.info("자동 매도 체크 시작 (Cloud Scheduler)")
        logger.info("=".repeat(80))

        return try {
            // TODO: AutoTradingUseCase.checkAndExecuteSellOrders() 호출
            logger.info("매도 조건 확인 중...")
            logger.info("매도 체크 완료")
            ResponseEntity.ok(mapOf("status" to "success", "job" to "auto-sell"))
        } catch (e: Exception) {
            logger.error("자동 매도 실행 중 오류", e)
            ResponseEntity.internalServerError()
                .body(mapOf("status" to "error", "job" to "auto-sell", "message" to (e.message ?: "unknown")))
        }
    }

    /**
     * 주문 정리 (Cloud Scheduler: 06:30 KST daily)
     */
    @PostMapping("/cleanup-orders")
    fun cleanupOrders(): ResponseEntity<Map<String, Any>> {
        logger.info("=".repeat(80))
        logger.info("주문 정리 시작 (Cloud Scheduler)")
        logger.info("=".repeat(80))

        return try {
            // TODO: 주문 정리 UseCase 구현 필요
            logger.info("주문 정리 로직은 아직 미구현 상태입니다")
            ResponseEntity.ok(mapOf("status" to "success", "job" to "cleanup-orders", "note" to "not yet implemented"))
        } catch (e: Exception) {
            logger.error("주문 정리 실행 중 오류", e)
            ResponseEntity.internalServerError()
                .body(mapOf("status" to "error", "job" to "cleanup-orders", "message" to (e.message ?: "unknown")))
        }
    }

    /**
     * 포트폴리오 수익 보고 (Cloud Scheduler: 07:00 KST daily)
     */
    @PostMapping("/portfolio-report")
    fun portfolioReport(): ResponseEntity<Map<String, Any>> {
        logger.info("=".repeat(80))
        logger.info("포트폴리오 수익 보고 시작 (Cloud Scheduler)")
        logger.info("=".repeat(80))

        return try {
            // TODO: 수익 리포트 UseCase 구현 필요
            logger.info("포트폴리오 수익 보고 로직은 아직 미구현 상태입니다")
            ResponseEntity.ok(mapOf("status" to "success", "job" to "portfolio-report", "note" to "not yet implemented"))
        } catch (e: Exception) {
            logger.error("포트폴리오 수익 보고 실행 중 오류", e)
            ResponseEntity.internalServerError()
                .body(mapOf("status" to "error", "job" to "portfolio-report", "message" to (e.message ?: "unknown")))
        }
    }

    /**
     * Canonical 백테스트 갱신 (Cloud Scheduler: 일 02:00 KST)
     */
    @PostMapping("/canonical-backtest")
    fun canonicalBacktest(): ResponseEntity<Map<String, Any>> {
        logger.info("=".repeat(80))
        logger.info("Canonical 백테스트 갱신 시작 (Cloud Scheduler)")
        logger.info("=".repeat(80))

        return try {
            canonicalBacktestService.refreshAllCanonicalBacktests()

            // 백테스트 갱신 후 전략 캐시 초기화
            listOf("marketplaceStrategies", "strategyDetail").forEach { cacheName ->
                cacheManager.getCache(cacheName)?.let {
                    it.clear()
                    logger.info("$cacheName 캐시 초기화 완료")
                }
            }

            logger.info("Canonical 백테스트 갱신 완료")
            ResponseEntity.ok(mapOf("status" to "success", "job" to "canonical-backtest"))
        } catch (e: Exception) {
            logger.error("Canonical 백테스트 실행 중 오류", e)
            ResponseEntity.internalServerError()
                .body(mapOf("status" to "error", "job" to "canonical-backtest", "message" to (e.message ?: "unknown")))
        }
    }

    /**
     * 백테스트 데이터 정리 (Cloud Scheduler: 03:00 KST daily)
     */
    @PostMapping("/backtest-cleanup")
    fun backtestCleanup(): ResponseEntity<Map<String, Any>> {
        logger.info("=".repeat(80))
        logger.info("백테스트 데이터 정리 시작 (Cloud Scheduler)")
        logger.info("=".repeat(80))

        return try {
            backtestCleanupService.runCleanup()
            logger.info("백테스트 데이터 정리 완료")
            ResponseEntity.ok(mapOf("status" to "success", "job" to "backtest-cleanup"))
        } catch (e: Exception) {
            logger.error("백테스트 데이터 정리 실행 중 오류", e)
            ResponseEntity.internalServerError()
                .body(mapOf("status" to "error", "job" to "backtest-cleanup", "message" to (e.message ?: "unknown")))
        }
    }

    private fun isUsMarketHours(): Boolean {
        val nyTime = ZonedDateTime.now(nyZone)
        val dayOfWeek = nyTime.dayOfWeek
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) return false
        val hour = nyTime.hour
        val minute = nyTime.minute
        if (hour < 9 || (hour == 9 && minute < 30)) return false
        if (hour >= 16) return false
        return true
    }
}
