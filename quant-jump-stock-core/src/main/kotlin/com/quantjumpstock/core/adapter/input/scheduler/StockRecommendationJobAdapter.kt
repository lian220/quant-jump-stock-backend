package com.quantjumpstock.core.adapter.input.scheduler

import com.quantjumpstock.core.domain.analysis.port.input.AnalysisUseCase
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

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
    private val analysisUseCase: AnalysisUseCase,
    private val cacheManager: CacheManager
) : Job {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun execute(context: JobExecutionContext?) {
        try {
            logger.info("🏆 StockRecommendationJob 시작: Composite Score 기반 종목 추천")

            val result = analysisUseCase.triggerStockRecommendation()
                .get(3, TimeUnit.MINUTES)

            // 스케줄러 완료 후 캐시 evict → 다음 조회 시 최신 데이터 반영
            listOf("buySignals", "recentPredictions", "predictionStats", "tickerPredictions", "marketplaceStrategies", "strategyDetail")
                .forEach { cacheName ->
                    cacheManager.getCache(cacheName)?.clear()
                    logger.info("🗑️ $cacheName 캐시 초기화 완료")
                }

            logger.info("✅ 종목 추천 완료: $result")

        } catch (e: Exception) {
            logger.error("❌ 종목 추천 Job 실행 중 오류", e)
            throw JobExecutionException(e)
        }
    }
}
