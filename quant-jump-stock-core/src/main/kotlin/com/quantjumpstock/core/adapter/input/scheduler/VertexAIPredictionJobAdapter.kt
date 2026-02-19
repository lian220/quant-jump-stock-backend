package com.quantjumpstock.core.adapter.input.scheduler

import com.quantjumpstock.core.application.vertexai.VertexAIService
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Vertex AI 예측 Job (Input Adapter)
 * 매일 23:45에 실행됩니다.
 *
 * 역할:
 * - 경제 데이터 + 기술적 분석이 완료된 후 AI 주가 예측 실행
 * - 예측 소요 시간: 약 30-35분
 * - Vertex AI CustomJob → 결과는 stock_predictions 컬렉션에 저장
 *
 * 의존관계:
 * - 23:00 경제 데이터 재수집 완료 필요
 * - 23:30 기술적 분석 완료 필요
 *
 * 참고: GCP가 활성화되지 않으면 이 Job은 등록되지 않습니다.
 */
@Component
@ConditionalOnProperty(
    name = ["gcp.vertex-ai.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class VertexAIPredictionJobAdapter(
    private val vertexAIService: VertexAIService
) : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext?) {
        try {
            val triggerName = context?.trigger?.key?.name ?: "unknown"
            logger.info("=".repeat(80))
            logger.info("Vertex AI 주가 예측 시작 (23:45) [Trigger: $triggerName]")
            logger.info("예상 소요 시간: 30-35분")
            logger.info("=".repeat(80))

            // Vertex AI 예측 실행 (Pub/Sub 경로)
            logger.info("Vertex AI 예측 실행 중 (Pub/Sub → Data Engine → Vertex AI)...")
            val result = vertexAIService.runPrediction()
            logger.info("✅ Vertex AI 예측 요청 완료: requestId=${result.requestId}")
            logger.info("   → CustomJob 실행 중... (약 00:15-00:20 완료 예정)")

            logger.info("=".repeat(80))
            logger.info("Vertex AI 예측 Job 완료 (요청 발행)")
            logger.info("=".repeat(80))

        } catch (e: Exception) {
            logger.error("❌ Vertex AI 예측 Job 실행 중 오류", e)
            throw JobExecutionException(e)
        }
    }
}
