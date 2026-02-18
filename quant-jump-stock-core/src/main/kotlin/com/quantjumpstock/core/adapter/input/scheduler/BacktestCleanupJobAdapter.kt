package com.quantjumpstock.core.adapter.input.scheduler

import com.quantjumpstock.core.application.backtest.BacktestCleanupService
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * SCRUM-344: 백테스트 데이터 정리 Job (Input Adapter)
 * 매일 03:00 KST에 실행됩니다.
 *
 * 역할:
 * - RUNNING 24시간 초과 → FAILED 처리
 * - USER_CUSTOM 초과 분 아카이브
 * - CANONICAL 오래된 것 정리
 */
@Component
class BacktestCleanupJobAdapter(
    private val backtestCleanupService: BacktestCleanupService
) : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext?) {
        try {
            val triggerName = context?.trigger?.key?.name ?: "unknown"
            logger.info("=".repeat(80))
            logger.info("백테스트 데이터 정리 시작 (매일 03:00) [Trigger: $triggerName]")
            logger.info("=".repeat(80))

            backtestCleanupService.runCleanup()

            logger.info("백테스트 데이터 정리 완료")
            logger.info("=".repeat(80))
        } catch (e: Exception) {
            logger.error("백테스트 데이터 정리 Job 실행 중 오류", e)
            throw JobExecutionException(e)
        }
    }
}
