package com.quantjumpstock.core.adapter.input.scheduler

import com.quantjumpstock.core.application.backtest.CanonicalBacktestService
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component

/**
 * SCRUM-344: Canonical Backtest Job (Input Adapter)
 * 매주 일요일 02:00 KST에 실행됩니다.
 *
 * 역할:
 * - PUBLISHED 상태인 모든 전략에 대해 대표 백테스트 실행
 * - 표준 파라미터: 1년, 1천만원, SPY 벤치마크
 */
@Component
class CanonicalBacktestJobAdapter(
    private val canonicalBacktestService: CanonicalBacktestService,
    private val cacheManager: CacheManager
) : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext?) {
        try {
            val triggerName = context?.trigger?.key?.name ?: "unknown"
            logger.info("=".repeat(80))
            logger.info("Canonical 백테스트 갱신 시작 (매주 일요일 02:00) [Trigger: $triggerName]")
            logger.info("=".repeat(80))

            canonicalBacktestService.refreshAllCanonicalBacktests()

            // 백테스트 갱신 후 전략 캐시 초기화 → 다음 조회 시 최신 데이터 반영
            listOf("marketplaceStrategies", "strategyDetail").forEach { cacheName ->
                cacheManager.getCache(cacheName)?.clear()
                logger.info("🗑️ $cacheName 캐시 초기화 완료")
            }

            logger.info("Canonical 백테스트 갱신 완료")
            logger.info("=".repeat(80))
        } catch (e: Exception) {
            logger.error("Canonical 백테스트 Job 실행 중 오류", e)
            throw JobExecutionException(e)
        }
    }
}
