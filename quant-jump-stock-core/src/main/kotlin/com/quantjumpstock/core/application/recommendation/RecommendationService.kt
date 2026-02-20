package com.quantjumpstock.core.application.recommendation

import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import com.quantjumpstock.core.domain.model.stock.StockPriceSnapshot
import com.quantjumpstock.core.domain.port.output.StockPriceDataPort
import com.quantjumpstock.core.domain.prediction.port.output.PredictionResultRepositoryPort
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Recommendation Service
 *
 * Composite Score 기반 통합 추천 시스템 비즈니스 로직.
 * PostgreSQL prediction_results 테이블에서 데이터 조회.
 *
 * 캐싱: buySignals 캐시 (TTL 6시간)
 * - 데이터는 스케줄러(00:20 KST)에 의해 1일 1회 생성되어 불변
 * - 빈 결과(count=0)는 캐싱하지 않음 (스케줄러 실행 전 조회 대응)
 */
@Service
class RecommendationService(
    private val predictionResultRepository: PredictionResultRepositoryPort,
    private val stockPriceDataPort: StockPriceDataPort
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 매수 신호 조회
     *
     * ⚠️ 중요: 스케줄러가 00:20(KST)에 실행되어 전날 날짜로 데이터를 저장하므로,
     *         전날 날짜로 조회해야 함. (당일 조회 시 데이터 없음)
     *
     * @param date 조회 날짜 (Controller에서 기본값 전날 날짜로 해소)
     * @param minConfidence 최소 신뢰도 (기본값 0.7)
     *                     Composite Score 기준으로 변환: minConfidence × 7.5
     *                     예: 0.7 → 5.25점
     * @return 매수 신호 응답
     */
    @Cacheable(
        value = ["buySignals"],
        key = "#date.toString() + '_' + #minConfidence",
        unless = "#result.count == 0"
    )
    fun getBuySignals(date: LocalDate, minConfidence: Double = 0.7): BuySignalsResponse {
        // Composite Score 기준으로 조회 (0.7 → 5.25점)
        // 기존 confidence(0~1)를 Composite Score(0~7.5) 기준으로 변환
        val minCompositeScore = minConfidence * 7.5

        logger.info("Fetching buy signals for date={}, minCompositeScore={}", date, minCompositeScore)

        // ✅ 부분 점수 시스템: Composite Score 기준으로만 필터링
        val buySignals = predictionResultRepository.findHighConfidenceBuySignals(
            date,
            minCompositeScore
        )  // is_recommended 필터 제거 (부분 점수 허용)

        logger.info("Found {} stocks with compositeScore >= {}", buySignals.size, minCompositeScore)

        val priceMap = loadPricesSafely(buySignals.map { it.ticker }.distinct())

        return BuySignalsResponse(
            success = true,
            date = date,
            minConfidence = minConfidence,
            count = buySignals.size,
            buySignals = buySignals.map { it.toBuySignalDto(priceMap[it.ticker]) }
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
     * 가격 데이터 안전 조회 (Graceful Degradation)
     */
    private fun loadPricesSafely(tickers: List<String>): Map<String, StockPriceSnapshot> {
        return try {
            stockPriceDataPort.getLatestPrices(tickers)
        } catch (e: Exception) {
            logger.warn("가격 데이터 일괄 조회 실패, 가격 없이 반환: ${e.message}")
            emptyMap()
        }
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
 *
 * @param priceSnapshot MongoDB에서 조회한 최신 가격 (null이면 DB 값 사용)
 */
private fun PredictionResult.toBuySignalDto(priceSnapshot: StockPriceSnapshot? = null): BuySignalDto {
    val enrichedCurrentPrice = priceSnapshot?.close ?: currentPrice
    val enrichedUpsidePercent = if (enrichedCurrentPrice != null && targetPrice != null &&
        enrichedCurrentPrice.compareTo(BigDecimal.ZERO) != 0
    ) {
        targetPrice!!.subtract(enrichedCurrentPrice)
            .divide(enrichedCurrentPrice, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .setScale(2, RoundingMode.HALF_UP)
    } else {
        upsidePercent
    }

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
        currentPrice = enrichedCurrentPrice,
        targetPrice = targetPrice,
        upsidePercent = enrichedUpsidePercent,
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
