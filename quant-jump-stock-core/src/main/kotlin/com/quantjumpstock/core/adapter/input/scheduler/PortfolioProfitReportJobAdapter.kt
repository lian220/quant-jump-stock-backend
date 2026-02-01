package com.quantjumpstock.core.adapter.input.scheduler

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 포트폴리오 수익 보고 Job (Input Adapter)
 * 매일 07:00에 실행됩니다.
 *
 * 역할:
 * - 사용자별 포트폴리오 수익률 계산
 * - 일일 수익 리포트 생성
 * - Slack 알림 발송
 */
@Component
class PortfolioProfitReportJobAdapter : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext?) {
        try {
            val triggerName = context?.trigger?.key?.name ?: "unknown"
            logger.info("=".repeat(80))
            logger.info("포트폴리오 수익 보고 시작 (07:00) [Trigger: $triggerName]")
            logger.info("=".repeat(80))

            // TODO: 수익 리포트 UseCase 구현 필요
            // reportingUseCase.generateDailyProfitReport()
            // - 사용자별 포트폴리오 조회
            // - 당일 수익률 계산
            // - Slack 알림 발송

            logger.info("⚠️ 포트폴리오 수익 보고 로직은 아직 미구현 상태입니다")
            logger.info("=".repeat(80))
        } catch (e: Exception) {
            logger.error("❌ 포트폴리오 수익 보고 Job 실행 중 오류", e)
            throw JobExecutionException(e)
        }
    }
}
