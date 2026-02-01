package com.quantjumpstock.core.adapter.input.scheduler

import com.quantjumpstock.core.application.trading.AutoTradingService
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 자동 매수 Job (Input Adapter)
 * 매일 23:50에 실행됩니다.
 *
 * 역할:
 * - 통합 분석 결과를 기반으로 자동 매수 실행
 * - 활성 사용자의 거래 설정에 따라 주문 생성
 * - PostgreSQL에 거래 기록 저장
 */
@Component
class AutoBuyJobAdapter(
    private val autoTradingService: AutoTradingService
) : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext?) {
        try {
            val triggerName = context?.trigger?.key?.name ?: "unknown"
            logger.info("=".repeat(80))
            logger.info("자동 매수 시작 (23:50) [Trigger: $triggerName]")
            logger.info("=".repeat(80))

            // AutoTradingService 호출
            autoTradingService.executeAutoTrading()

            logger.info("✅ 자동 매수 완료")
            logger.info("=".repeat(80))
        } catch (e: Exception) {
            logger.error("❌ 자동 매수 Job 실행 중 오류", e)
            throw JobExecutionException(e)
        }
    }
}
