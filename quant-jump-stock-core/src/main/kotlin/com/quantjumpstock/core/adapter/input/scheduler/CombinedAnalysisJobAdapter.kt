package com.quantjumpstock.core.adapter.input.scheduler

import com.quantjumpstock.core.domain.analysis.port.input.AnalysisUseCase
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 통합 분석 Job (Input Adapter)
 * 매일 23:45에 실행됩니다.
 *
 * 역할:
 * - 기술적 분석 + 감정 분석 + 경제 데이터 통합
 * - 최종 종합 점수 계산
 * - 투자 추천 생성
 */
@Component
class CombinedAnalysisJobAdapter(
    private val analysisUseCase: AnalysisUseCase
) : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext?) {
        try {
            val triggerName = context?.trigger?.key?.name ?: "unknown"
            logger.info("=".repeat(80))
            logger.info("통합 분석 시작 (23:45) [Trigger: $triggerName]")
            logger.info("=".repeat(80))

            // UseCase 호출
            analysisUseCase.triggerCombinedAnalysis()
                .thenAccept { result ->
                    logger.info("✅ 통합 분석 요청 완료: $result")
                }
                .exceptionally { e ->
                    logger.error("❌ 통합 분석 요청 실패", e)
                    null
                }
                .get() // 비동기 완료 대기

            logger.info("=".repeat(80))
        } catch (e: Exception) {
            logger.error("❌ 통합 분석 Job 실행 중 오류", e)
            throw JobExecutionException(e)
        }
    }
}
