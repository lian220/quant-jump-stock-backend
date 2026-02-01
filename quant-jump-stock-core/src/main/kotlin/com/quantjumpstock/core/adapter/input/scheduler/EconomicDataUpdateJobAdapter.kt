package com.quantjumpstock.core.adapter.input.scheduler

import com.quantjumpstock.core.domain.economic.port.input.EconomicDataUseCase
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 경제 데이터 업데이트 Job (Input Adapter)
 * Quartz 스케줄러의 트리거를 받아 UseCase로 전달하는 Adapter 역할을 합니다.
 * 매일 06:05와 23:00에 실행됩니다.
 */
@Component
class EconomicDataUpdateJobAdapter(
    private val economicDataUseCase: EconomicDataUseCase
) : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext?) {
        try {
            val triggerName = context?.trigger?.key?.name ?: "unknown"
            logger.info("=".repeat(80))
            logger.info("경제 데이터 업데이트 시작 [Trigger: $triggerName]")
            logger.info("=".repeat(80))

            // UseCase 호출
            economicDataUseCase.triggerEconomicDataUpdate()
                .thenAccept { result ->
                    logger.info("✅ 경제 데이터 업데이트 요청 완료: $result")
                }
                .exceptionally { e ->
                    logger.error("❌ 경제 데이터 업데이트 요청 실패", e)
                    null
                }
                .get() // 비동기 완료 대기

            logger.info("=".repeat(80))
        } catch (e: Exception) {
            logger.error("❌ 경제 데이터 업데이트 Job 실행 중 오류", e)
            throw JobExecutionException(e)
        }
    }
}
