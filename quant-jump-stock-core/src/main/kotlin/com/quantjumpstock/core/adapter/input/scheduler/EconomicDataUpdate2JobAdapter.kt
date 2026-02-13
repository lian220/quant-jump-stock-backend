package com.quantjumpstock.core.adapter.input.scheduler

import com.quantjumpstock.core.domain.economic.port.input.EconomicDataUseCase
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 경제 데이터 재수집 Job (Input Adapter)
 * 매일 23:00에 실행됩니다.
 *
 * 역할:
 * - 경제 데이터 재수집 (FRED + Yahoo Finance 지표 업데이트)
 * - CPI/NFP, ISM PMI 등 주요 경제 지표 최신화
 *
 * 의존관계:
 * - CPI/NFP 발표: 21:30 KST (EDT), 22:30 KST (EST)
 * - ISM PMI 발표: 23:00 KST (EDT), 00:00 KST (EST)
 * - 23:00 실행으로 EST/EDT 모두 커버
 *
 * 참고:
 * - Vertex AI 예측은 별도 Job(23:45)에서 실행
 * - GCP 활성화 여부와 무관하게 항상 실행
 */
@Component
class EconomicDataUpdate2JobAdapter(
    private val economicDataUseCase: EconomicDataUseCase
) : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext?) {
        try {
            val triggerName = context?.trigger?.key?.name ?: "unknown"
            logger.info("=".repeat(80))
            logger.info("경제 데이터 재수집 시작 (23:00) [Trigger: $triggerName]")
            logger.info("=".repeat(80))

            // 경제 데이터 업데이트 (타임아웃: 5분)
            logger.info("경제 데이터 재수집 중 (FRED + Yahoo Finance)...")
            economicDataUseCase.triggerEconomicDataUpdate()
                .thenAccept { result ->
                    logger.info("✅ 경제 데이터 재수집 완료: $result")
                }
                .get(5, TimeUnit.MINUTES)

            logger.info("=".repeat(80))
            logger.info("경제 데이터 재수집 완료")
            logger.info("=".repeat(80))

        } catch (e: TimeoutException) {
            logger.error("❌ 경제 데이터 재수집 타임아웃 (5분 초과)", e)
            throw JobExecutionException("경제 데이터 재수집이 타임아웃되었습니다", e)
        } catch (e: InterruptedException) {
            logger.error("❌ 경제 데이터 재수집 중단됨", e)
            Thread.currentThread().interrupt()
            throw JobExecutionException("경제 데이터 재수집이 중단되었습니다", e)
        } catch (e: Exception) {
            logger.error("❌ 경제 데이터 재수집 Job 실행 중 오류", e)
            throw JobExecutionException(e)
        }
    }
}
