package com.quantjumpstock.core.application.recommendation

import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import com.quantjumpstock.core.domain.prediction.port.output.PredictionResultRepositoryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Recommendation Service
 *
 * Composite Score 기반 통합 추천 시스템 비즈니스 로직.
 * PostgreSQL prediction_results 테이블에서 데이터 조회.
 */
@Service
class RecommendationService(
    private val predictionResultRepository: PredictionResultRepositoryPort
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 매수 신호 조회
     *
     * ⚠️ 중요: 스케줄러가 23:05(KST)에 실행되어 전날 날짜로 데이터를 저장하므로,
     *         전날 날짜로 조회해야 함. (당일 조회 시 데이터 없음)
     *
     * @param date 조회 날짜 (null이면 전날 날짜)
     * @param minConfidence 최소 신뢰도 (기본값 0.7)
     *                     Composite Score 기준으로 변환: minConfidence × 7.5
     *                     예: 0.7 → 5.25점
     * @return 매수 신호 응답
     */
    fun getBuySignals(date: LocalDate? = null, minConfidence: Double = 0.7): BuySignalsResponse {
        // 날짜가 지정되지 않으면 전날 날짜 사용
        val targetDate = date ?: LocalDate.now().minusDays(1)

        // Composite Score 기준으로 조회 (0.7 → 5.25점)
        // 기존 confidence(0~1)를 Composite Score(0~7.5) 기준으로 변환
        val minCompositeScore = minConfidence * 7.5

        logger.info("Fetching buy signals for date={}, minCompositeScore={}", targetDate, minCompositeScore)

        val buySignals = predictionResultRepository.findHighConfidenceBuySignals(
            targetDate,
            minCompositeScore
        ).filter { it.isRecommended }  // 추천 종목만 필터링

        logger.info("Found {} recommended stocks", buySignals.size)

        return BuySignalsResponse(
            success = true,
            date = targetDate,
            minConfidence = minConfidence,
            count = buySignals.size,
            buySignals = buySignals.map { it.toBuySignalDto() }
        )
    }

    /**
     * 특정 종목의 최근 예측 결과 조회
     */
    fun getPredictionsByTicker(ticker: String, limit: Int = 30): TickerPredictionsResponse {
        val predictions = predictionResultRepository.findByTickerOrderByDateDesc(ticker)
            .take(limit)

        return TickerPredictionsResponse(
            success = true,
            ticker = ticker,
            count = predictions.size,
            predictions = predictions
        )
    }

    /**
     * 최근 예측 결과 조회
     */
    fun getRecentPredictions(days: Int = 7): RecentPredictionsResponse {
        val fromDate = LocalDate.now().minusDays(days.toLong())
        val predictions = predictionResultRepository.findRecentPredictions(fromDate)

        return RecentPredictionsResponse(
            success = true,
            fromDate = fromDate,
            count = predictions.size,
            predictions = predictions
        )
    }
}

/**
 * PredictionResult를 BuySignalDto로 변환
 */
private fun PredictionResult.toBuySignalDto(): BuySignalDto {
    return BuySignalDto(
        ticker = ticker,
        stockName = stockName,
        analysisDate = analysisDate,
        compositeScore = compositeScore,
        compositeGrade = compositeGrade.name,
        aiScore = aiScore,
        techScore = techScore,
        sentimentScore = sentimentNormalizedScore,
        isRecommended = isRecommended,
        recommendationReason = recommendationReason,
        currentPrice = currentPrice,
        targetPrice = targetPrice,
        upsidePercent = upsidePercent,
        priceRecommendation = priceRecommendation
    )
}

/**
 * 매수 신호 응답
 */
data class BuySignalsResponse(
    val success: Boolean,
    val date: LocalDate,
    val minConfidence: Double,
    val count: Int,
    val buySignals: List<BuySignalDto>
)

/**
 * 매수 신호 DTO
 */
data class BuySignalDto(
    val ticker: String,
    val stockName: String,
    val analysisDate: LocalDate,
    val compositeScore: java.math.BigDecimal,
    val compositeGrade: String,
    val aiScore: java.math.BigDecimal,
    val techScore: java.math.BigDecimal,
    val sentimentScore: java.math.BigDecimal,
    val isRecommended: Boolean,
    val recommendationReason: String?,
    val currentPrice: java.math.BigDecimal?,
    val targetPrice: java.math.BigDecimal?,
    val upsidePercent: java.math.BigDecimal?,
    val priceRecommendation: String?
)

/**
 * 종목별 예측 결과 응답
 */
data class TickerPredictionsResponse(
    val success: Boolean,
    val ticker: String,
    val count: Int,
    val predictions: List<PredictionResult>
)

/**
 * 최근 예측 결과 응답
 */
data class RecentPredictionsResponse(
    val success: Boolean,
    val fromDate: LocalDate,
    val count: Int,
    val predictions: List<PredictionResult>
)
