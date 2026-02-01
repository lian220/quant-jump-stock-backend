package com.quantjumpstock.core.adapter.input.scheduler

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 주문 정리 Job (Input Adapter)
 * 매일 06:30에 실행됩니다.
 *
 * 역할:
 * - 미체결 주문 정리
 * - 만료된 주문 취소
 * - 주문 상태 동기화 (KIS API와 DB)
 */
@Component
class CleanupOrdersJobAdapter : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext?) {
        try {
            val triggerName = context?.trigger?.key?.name ?: "unknown"
            logger.info("=".repeat(80))
            logger.info("주문 정리 시작 (06:30) [Trigger: $triggerName]")
            logger.info("=".repeat(80))

            // TODO: 주문 정리 UseCase 구현 필요
            // orderManagementUseCase.cleanupOrders()
            // - PENDING 상태 주문 확인
            // - 만료된 주문 CANCELLED 처리
            // - KIS API와 주문 상태 동기화

            logger.info("⚠️ 주문 정리 로직은 아직 미구현 상태입니다")
            logger.info("=".repeat(80))
        } catch (e: Exception) {
            logger.error("❌ 주문 정리 Job 실행 중 오류", e)
            throw JobExecutionException(e)
        }
    }
}
