package com.quantjumpstock.core.application.prediction

import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import com.quantjumpstock.core.domain.model.stock.StockPriceSnapshot
import com.quantjumpstock.core.domain.port.output.StockPriceDataPort
import com.quantjumpstock.core.domain.prediction.port.output.PredictionResultRepositoryPort
import com.quantjumpstock.core.domain.recommendation.model.RecommendationCriteria
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Prediction Application Service
 *
 * Composite Score 기반 통합 예측 결과 조회 비즈니스 로직.
 * PredictionResultRepositoryPort를 통해 PostgreSQL에 접근 (Hexagonal Architecture).
 *
 * 캐싱: Caffeine 수동 캐싱 (GraalVM Native Image SpEL 호환)
 * - buySignals: 6시간 TTL, StockRecommendationJob(00:20) 완료 시 초기화
 * - recentPredictions: 6시간 TTL, 동일 시점 초기화
 */
@Service
class PredictionService(
    private val predictionResultRepository: PredictionResultRepositoryPort,
    private val stockPriceDataPort: StockPriceDataPort,
    private val cacheManager: CacheManager
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 최근 예측 결과 조회
     */
    fun getAllPredictions(days: Int): PredictionListResponse {
        val fromDate = LocalDate.now().minusDays(days.toLong())
        val predictions = predictionResultRepository.findRecentPredictions(fromDate)
        val priceMap = loadPricesSafely(predictions.map { it.ticker }.distinct())

        return PredictionListResponse(
            success = true,
            count = predictions.size,
            fromDate = fromDate,
            predictions = predictions.map { it.toBuySignalDto(priceMap[it.ticker]) }
        )
    }

    /**
     * 최신 예측 결과 조회
     *
     * 가장 최근 데이터가 있는 날짜의 결과를 반환.
     */
    fun getLatestPredictions(): PredictionListResponse {
        // 캐시 확인
        val cache = cacheManager.getCache("recentPredictions")
        cache?.get("latest", PredictionListResponse::class.java)?.let { return it }

        val latestDate = predictionResultRepository.findLatestAnalysisDate()
            ?: LocalDate.now()

        val predictions = predictionResultRepository.findByDate(latestDate)
        val priceMap = loadPricesSafely(predictions.map { it.ticker }.distinct())

        val result = PredictionListResponse(
            success = true,
            count = predictions.size,
            fromDate = latestDate,
            predictions = predictions.map { it.toBuySignalDto(priceMap[it.ticker]) }
        )
        cache?.put("latest", result)
        return result
    }

    /**
     * 매수 신호 조회
     *
     * @param date 분석 날짜 (null이면 최신 데이터가 있는 날짜 사용)
     * @param minConfidence Composite Score 비율 (0.0~1.0), Score × 7.5로 변환
     * @return 매수 신호 응답
     */
    fun getBuySignals(date: LocalDate? = null, minConfidence: Double = 0.0): BuySignalsResponse {
        val cache = cacheManager.getCache("buySignals")

        // date가 null(프론트 기본 호출)이면 DB 조회 없이 고정 키로 캐시 체크
        // date가 지정되면 날짜별 캐시 키 사용
        val cacheKey = "${date ?: "latest"}:${minConfidence}"
        cache?.get(cacheKey, BuySignalsResponse::class.java)?.let { return it }

        // 캐시 미스: 날짜 결정 후 DB 조회
        val targetDate = date
            ?: predictionResultRepository.findLatestAnalysisDate()
            ?: LocalDate.now().minusDays(1)

        // Composite Score 기준으로 변환 (0~1 → 0~7.5)
        val minCompositeScore = minConfidence * 7.5

        logger.info("📊 매수 신호 조회 (캐시 미스): date=$targetDate, minCompositeScore=$minCompositeScore")

        val buySignals = predictionResultRepository.findHighConfidenceBuySignals(
            targetDate,
            minCompositeScore
        )
        val priceMap = loadPricesSafely(buySignals.map { it.ticker }.distinct())

        val result = BuySignalsResponse(
            success = true,
            date = targetDate,
            minConfidence = minConfidence,
            count = buySignals.size,
            buySignals = buySignals.map { it.toBuySignalDto(priceMap[it.ticker]) }
        )
        cache?.put(cacheKey, result)
        return result
    }

    /**
     * 특정 종목의 예측 결과 조회
     */
    fun getPredictionsBySymbol(symbol: String, limit: Int): SymbolPredictionsResponse {
        val predictions = predictionResultRepository.findByTickerOrderByDateDesc(symbol)
            .take(limit)
        val priceMap = loadPricesSafely(predictions.map { it.ticker }.distinct())

        return SymbolPredictionsResponse(
            success = true,
            symbol = symbol,
            count = predictions.size,
            predictions = predictions.map { it.toBuySignalDto(priceMap[it.ticker]) }
        )
    }

    /**
     * 특정 날짜의 예측 결과 조회
     */
    fun getPredictionsByDate(date: LocalDate): PredictionListResponse {
        val predictions = predictionResultRepository.findByDate(date)
        val priceMap = loadPricesSafely(predictions.map { it.ticker }.distinct())

        return PredictionListResponse(
            success = true,
            count = predictions.size,
            fromDate = date,
            predictions = predictions.map { it.toBuySignalDto(priceMap[it.ticker]) }
        )
    }

    /**
     * 예측 통계 조회
     */
    fun getPredictionStats(days: Int): PredictionStatsResponse {
        // 캐시 확인
        val cache = cacheManager.getCache("predictionStats")
        cache?.get(days, PredictionStatsResponse::class.java)?.let { return it }

        val fromDate = LocalDate.now().minusDays(days.toLong())
        val predictions = predictionResultRepository.findRecentPredictions(fromDate)

        val stats = calculateStats(predictions)

        val result = PredictionStatsResponse(
            success = true,
            period = "${fromDate} ~ ${LocalDate.now()}",
            stats = stats
        )
        cache?.put(days, result)
        return result
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
     * 통계 계산
     */
    private fun calculateStats(predictions: List<PredictionResult>): Map<String, Any> {
        if (predictions.isEmpty()) {
            return mapOf(
                "total" to 0,
                "message" to "데이터 없음"
            )
        }

        val recommendedCount = predictions.count { it.isRecommended }
        val avgScore = predictions.map { it.compositeScore.toDouble() }.average()

        val gradeDistribution = predictions.groupBy { it.compositeGrade.name }
            .mapValues { it.value.size }

        return mapOf(
            "total" to predictions.size,
            "recommended" to recommendedCount,
            "averageCompositeScore" to String.format("%.2f", avgScore),
            "gradeDistribution" to gradeDistribution,
            "uniqueTickers" to predictions.map { it.ticker }.distinct().size
        )
    }
}

/**
 * PredictionResult → API 응답용 DTO 변환
 *
 * @param priceSnapshot MongoDB에서 조회한 최신 가격 (null이면 DB 값 사용)
 */
fun PredictionResult.toBuySignalDto(priceSnapshot: StockPriceSnapshot? = null): Map<String, Any?> {
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

    fun normalize(score: BigDecimal, max: BigDecimal): Int =
        score.divide(max, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .toInt()
            .coerceIn(0, 100)

    val hasAi = aiScore.compareTo(BigDecimal.ZERO) != 0
    val hasSentiment = sentimentNormalizedScore.compareTo(BigDecimal.ZERO) != 0
    val hasTech = techScore.compareTo(BigDecimal.ZERO) != 0
    val weights = mutableListOf<Pair<BigDecimal, BigDecimal>>()
    if (hasTech) weights.add(RecommendationCriteria.WEIGHT_TECH to RecommendationCriteria.MAX_TECH_SCORE)
    if (hasAi) weights.add(RecommendationCriteria.WEIGHT_AI to RecommendationCriteria.MAX_AI_SCORE)
    if (hasSentiment) weights.add(RecommendationCriteria.WEIGHT_SENTIMENT to RecommendationCriteria.MAX_SENTIMENT_SCORE)
    val totalWeight = weights.sumOf { it.first }
    val compositeMax = if (totalWeight > BigDecimal.ZERO)
        weights.sumOf { (w, mx) -> w.divide(totalWeight, 4, RoundingMode.HALF_UP) * mx }
    else RecommendationCriteria.MAX_SCORE

    return mapOf(
        "ticker" to ticker,
        "stockName" to stockName,
        "analysisDate" to analysisDate.toString(),
        "compositeScore" to compositeScore.toDouble(),
        "compositeGrade" to compositeGrade.name,
        "aiScore" to aiScore.toDouble(),
        "techScore" to techScore.toDouble(),
        "sentimentScore" to sentimentNormalizedScore.toDouble(),
        // 정규화 점수 (0-100)
        "techScoreDisplay" to normalize(techScore, RecommendationCriteria.MAX_TECH_SCORE),
        "aiScoreDisplay" to normalize(aiScore, RecommendationCriteria.MAX_AI_SCORE),
        "sentimentScoreDisplay" to normalize(sentimentNormalizedScore, RecommendationCriteria.MAX_SENTIMENT_SCORE),
        "compositeScoreDisplay" to normalize(compositeScore, compositeMax),
        "isRecommended" to isRecommended,
        "recommendationReason" to recommendationReason,
        "currentPrice" to enrichedCurrentPrice?.toDouble(),
        "targetPrice" to targetPrice?.toDouble(),
        "upsidePercent" to enrichedUpsidePercent?.toDouble(),
        "priceRecommendation" to priceRecommendation
    )
}

/**
 * 예측 결과 목록 응답
 */
data class PredictionListResponse(
    val success: Boolean,
    val count: Int,
    val fromDate: LocalDate,
    val predictions: List<Map<String, Any?>>
)

/**
 * 매수 신호 응답
 */
data class BuySignalsResponse(
    val success: Boolean,
    val date: LocalDate,
    val minConfidence: Double,
    val count: Int,
    val buySignals: List<Map<String, Any?>>
)

/**
 * 종목별 예측 응답
 */
data class SymbolPredictionsResponse(
    val success: Boolean,
    val symbol: String,
    val count: Int,
    val predictions: List<Map<String, Any?>>
)

/**
 * 예측 통계 응답
 */
data class PredictionStatsResponse(
    val success: Boolean,
    val period: String,
    val stats: Map<String, Any>
)
