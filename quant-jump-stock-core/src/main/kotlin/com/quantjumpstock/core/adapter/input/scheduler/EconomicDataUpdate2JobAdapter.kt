package com.quantjumpstock.core.adapter.input.scheduler

import com.quantjumpstock.core.adapter.output.gcp.vertexai.VertexAIJobService
import com.quantjumpstock.core.domain.economic.port.input.EconomicDataUseCase
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 경제 데이터 재수집 + Vertex AI 예측 Job (Input Adapter)
 * 매일 22:00에 실행됩니다.
 *
 * 역할:
 * - 경제 데이터 재수집 (경제지표 업데이트)
 * - Vertex AI 예측 모델 실행 (Google Cloud SDK 직접 호출)
 *
 * 참고: GCP가 활성화되지 않으면 이 Job은 등록되지 않습니다.
 */
@Component
@ConditionalOnProperty(
    prefix = "gcp",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class EconomicDataUpdate2JobAdapter(
    private val economicDataUseCase: EconomicDataUseCase,
    private val vertexAIJobService: VertexAIJobService
) : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext?) {
        try {
            val triggerName = context?.trigger?.key?.name ?: "unknown"
            logger.info("=".repeat(80))
            logger.info("경제 데이터 재수집 + Vertex AI 예측 시작 (22:00) [Trigger: $triggerName]")
            logger.info("=".repeat(80))

            // 1단계: 경제 데이터 업데이트
            logger.info("[1/2] 경제 데이터 재수집 중...")
            economicDataUseCase.triggerEconomicDataUpdate()
                .thenAccept { result ->
                    logger.info("✅ 경제 데이터 재수집 완료: $result")
                }
                .exceptionally { e ->
                    logger.error("❌ 경제 데이터 재수집 실패", e)
                    null
                }
                .get()

            // 2단계: Vertex AI 예측 실행
            logger.info("[2/2] Vertex AI 예측 실행 중...")
            val jobId = vertexAIJobService.createAndRunCustomJob()
            logger.info("✅ Vertex AI Job 실행 완료: $jobId")

            logger.info("=".repeat(80))
            logger.info("경제 데이터 재수집 + Vertex AI 예측 완료")
            logger.info("=".repeat(80))

        } catch (e: Exception) {
            logger.error("❌ 경제 데이터 재수집 + Vertex AI 예측 Job 실행 중 오류", e)
            throw JobExecutionException(e)
        }
    }
}
