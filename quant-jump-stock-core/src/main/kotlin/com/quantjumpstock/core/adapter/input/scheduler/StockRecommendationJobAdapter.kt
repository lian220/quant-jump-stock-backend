package com.quantjumpstock.core.adapter.input.scheduler

import com.quantjumpstock.core.domain.analysis.port.input.AnalysisUseCase
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 종목 추천 Job (Input Adapter)
 * 매일 00:20에 실행됩니다.
 *
 * 역할:
 * - Vertex AI 예측 완료 후 종목 추천 실행
 * - Composite Score 계산: AI(30%) + Technical(40%) + Sentiment(30%)
 * - 매수 후보 필터링 및 Slack 알림 발송
 *
 * 의존관계:
 * - 23:45 Vertex AI 예측 시작 → ~00:15-00:20 완료
 * - 23:30 기술적 분석 완료
 * - 23:30 감정 분석 완료
 *
 * 데이터 소스:
 * - daily_stock_data: 경제 지표 + 주가 + 기술 지표
 * - stock_predictions: AI 예측 결과
 * - news_sentiment: 감정 분석 결과
 */
@Component
class StockRecommendationJobAdapter(
    private val analysisUseCase: AnalysisUseCase
) : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext?) {
        try {
            val triggerName = context?.trigger?.key?.name ?: "unknown"
            logger.info("=".repeat(80))
            logger.info("종목 추천 시작 (00:20) [Trigger: $triggerName]")
            logger.info("Composite Score 기반 매수 후보 선정")
            logger.info("=".repeat(80))

            // 종목 추천 실행 (Kafka → Data Engine)
            logger.info("종목 추천 실행 중...")
            logger.info("  - AI 예측 결과 로드 (stock_predictions)")
            logger.info("  - 기술적 분석 결과 로드 (daily_stock_data.stocks)")
            logger.info("  - 감정 분석 결과 로드 (news_sentiment)")
            logger.info("  - Composite Score 계산 및 필터링")

            // TODO: 종목 추천 UseCase 메소드 구현 필요
            // val result = analysisUseCase.triggerStockRecommendation()
            // result.thenAccept { res ->
            //     logger.info("✅ 종목 추천 완료: ${res.candidateCount}개 종목")
            // }.get()

            logger.info("⚠️ 종목 추천 기능 구현 예정 (triggerStockRecommendation)")

            logger.info("=".repeat(80))
            logger.info("종목 추천 Job 완료")
            logger.info("=".repeat(80))

        } catch (e: Exception) {
            logger.error("❌ 종목 추천 Job 실행 중 오류", e)
            throw JobExecutionException(e)
        }
    }
}
