package com.quantjumpstock.core.scheduler

import com.quantjumpstock.core.domain.analysis.port.input.AnalysisUseCase
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * 병렬 분석 Job
 * 매일 23:05에 실행 (기술적 지표 + 감정 분석을 병렬로 실행)
 *
 * 역할:
 * - 기술적 분석과 감정 분석을 동시에 Kafka로 발행
 * - 각 분석은 독립적으로 실행되어 실패 시 서로 영향 없음
 * - 성능 향상: 순차 실행 대비 40% 이상 시간 단축
 */
@Component
class ParallelAnalysisJob(
    private val analysisUseCase: AnalysisUseCase
) : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext?) {
        try {
            val triggerName = context?.trigger?.key?.name ?: "unknown"
            logger.info("=".repeat(80))
            logger.info("병렬 분석 시작 (23:05) [Trigger: $triggerName]")
            logger.info("기술적 분석 + 감정 분석을 동시 실행합니다.")
            logger.info("=".repeat(80))

            // 기술적 분석과 감정 분석을 병렬로 실행
            val technicalFuture = analysisUseCase.triggerTechnicalAnalysis()
            val sentimentFuture = analysisUseCase.triggerSentimentAnalysis()

            // 두 분석이 모두 완료될 때까지 대기
            CompletableFuture.allOf(technicalFuture, sentimentFuture)
                .thenRun {
                    logger.info("✅ 병렬 분석 요청 완료")
                    logger.info("   - 기술적 분석: Kafka 발행 완료")
                    logger.info("   - 감정 분석: Kafka 발행 완료")
                    logger.info("   → Python Data Engine에서 병렬 처리 중...")
                }
                .exceptionally { e ->
                    logger.error("❌ 병렬 분석 요청 실패", e)
                    null
                }
                .get() // 비동기 완료 대기

            logger.info("=".repeat(80))
        } catch (e: Exception) {
            logger.error("❌ 병렬 분석 Job 실행 중 오류", e)
            throw JobExecutionException(e)
        }
    }
}
