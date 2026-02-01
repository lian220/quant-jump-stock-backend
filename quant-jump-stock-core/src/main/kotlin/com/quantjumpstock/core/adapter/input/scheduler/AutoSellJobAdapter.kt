package com.quantjumpstock.core.adapter.input.scheduler

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 자동 매도 Job (Input Adapter)
 * 매 1분마다 실행하여 매도 조건 확인
 *
 * 역할:
 * - 미국 시장 시간 검증 (9:30 AM - 4:00 PM ET, 평일만)
 * - 매도 조건 확인 (손절/익절)
 * - 매도 주문 실행
 */
@Component
class AutoSellJobAdapter : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val nyZone = ZoneId.of("America/New_York")

    override fun execute(context: JobExecutionContext?) {
        try {
            // 미국 시장 시간 검증
            if (!isUsMarketHours()) {
                return
            }

            val triggerName = context?.trigger?.key?.name ?: "unknown"
            logger.info("=".repeat(80))
            logger.info("자동 매도 체크 시작 [Trigger: $triggerName]")
            logger.info("=".repeat(80))

            // TODO: AutoTradingUseCase.checkAndExecuteSellOrders() 호출
            // 현재는 로깅만 수행
            logger.info("📊 매도 조건 확인 중...")
            logger.info("✅ 매도 체크 완료")

            logger.info("=".repeat(80))
        } catch (e: Exception) {
            logger.error("❌ 자동 매도 Job 실행 중 오류", e)
            throw JobExecutionException(e)
        }
    }

    /**
     * 미국 시장 시간 확인
     * 평일 9:30 AM - 4:00 PM ET
     */
    private fun isUsMarketHours(): Boolean {
        val nyTime = ZonedDateTime.now(nyZone)
        val hour = nyTime.hour
        val minute = nyTime.minute
        val dayOfWeek = nyTime.dayOfWeek

        // 주말 제외
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false
        }

        // 9:30 AM 이전
        if (hour < 9 || (hour == 9 && minute < 30)) {
            return false
        }

        // 4:00 PM 이후
        if (hour >= 16) {
            return false
        }

        return true
    }
}
