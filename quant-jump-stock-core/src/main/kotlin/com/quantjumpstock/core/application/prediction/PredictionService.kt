package com.quantjumpstock.core.application.prediction

import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import com.quantjumpstock.core.domain.prediction.port.output.PredictionResultRepositoryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Prediction Application Service
 *
 * Composite Score 기반 통합 예측 결과 조회 비즈니스 로직.
 * PredictionResultRepositoryPort를 통해 PostgreSQL에 접근 (Hexagonal Architecture).
 */
@Service
class PredictionService(
    private val predictionResultRepository: PredictionResultRepositoryPort
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 최근 예측 결과 조회
     */
    fun getAllPredictions(days: Int): PredictionListResponse {
        val fromDate = LocalDate.now().minusDays(days.toLong())
        val predictions = predictionResultRepository.findRecentPredictions(fromDate)

        return PredictionListResponse(
            success = true,
            count = predictions.size,
            fromDate = fromDate,
            predictions = predictions.map { it.toBuySignalDto() }
        )
    }

    /**
     * 최신 예측 결과 조회
     *
     * 가장 최근 데이터가 있는 날짜의 결과를 반환.
     */
    fun getLatestPredictions(): PredictionListResponse {
        val latestDate = predictionResultRepository.findLatestAnalysisDate()
            ?: LocalDate.now()

        val predictions = predictionResultRepository.findByDate(latestDate)

        return PredictionListResponse(
            success = true,
            count = predictions.size,
            fromDate = latestDate,
            predictions = predictions.map { it.toBuySignalDto() }
        )
    }

    /**
     * 매수 신호 조회
     *
     * @param date 분석 날짜 (null이면 최신 데이터가 있는 날짜 사용)
     * @param minConfidence Composite Score 비율 (0.0~1.0), Score × 7.5로 변환
     * @return 매수 신호 응답
     */
    fun getBuySignals(date: LocalDate? = null, minConfidence: Double = 0.0): BuySignalsResponse {
        // 날짜 결정: 지정된 날짜 > 최신 데이터 날짜 > 어제
        val targetDate = date
            ?: predictionResultRepository.findLatestAnalysisDate()
            ?: LocalDate.now().minusDays(1)

        // Composite Score 기준으로 변환 (0~1 → 0~7.5)
        val minCompositeScore = minConfidence * 7.5

        logger.info("📊 매수 신호 조회: date=$targetDate, minCompositeScore=$minCompositeScore")

        val buySignals = predictionResultRepository.findHighConfidenceBuySignals(
            targetDate,
            minCompositeScore
        )

        return BuySignalsResponse(
            success = true,
            date = targetDate,
            minConfidence = minConfidence,
            count = buySignals.size,
            buySignals = buySignals.map { it.toBuySignalDto() }
        )
    }

    /**
     * 특정 종목의 예측 결과 조회
     */
    fun getPredictionsBySymbol(symbol: String, limit: Int): SymbolPredictionsResponse {
        val predictions = predictionResultRepository.findByTickerOrderByDateDesc(symbol)
            .take(limit)

        return SymbolPredictionsResponse(
            success = true,
            symbol = symbol,
            count = predictions.size,
            predictions = predictions.map { it.toBuySignalDto() }
        )
    }

    /**
     * 특정 날짜의 예측 결과 조회
     */
    fun getPredictionsByDate(date: LocalDate): PredictionListResponse {
        val predictions = predictionResultRepository.findByDate(date)

        return PredictionListResponse(
            success = true,
            count = predictions.size,
            fromDate = date,
            predictions = predictions.map { it.toBuySignalDto() }
        )
    }

    /**
     * 예측 통계 조회
     */
    fun getPredictionStats(days: Int): PredictionStatsResponse {
        val fromDate = LocalDate.now().minusDays(days.toLong())
        val predictions = predictionResultRepository.findRecentPredictions(fromDate)

        val stats = calculateStats(predictions)

        return PredictionStatsResponse(
            success = true,
            period = "${fromDate} ~ ${LocalDate.now()}",
            stats = stats
        )
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
 */
fun PredictionResult.toBuySignalDto(): Map<String, Any?> = mapOf(
    "ticker" to ticker,
    "stockName" to stockName,
    "analysisDate" to analysisDate.toString(),
    "compositeScore" to compositeScore.toDouble(),
    "compositeGrade" to compositeGrade.name,
    "aiScore" to aiScore.toDouble(),
    "techScore" to techScore.toDouble(),
    "sentimentScore" to sentimentNormalizedScore.toDouble(),
    "isRecommended" to isRecommended,
    "recommendationReason" to recommendationReason,
    "currentPrice" to currentPrice?.toDouble(),
    "targetPrice" to targetPrice?.toDouble(),
    "upsidePercent" to upsidePercent?.toDouble(),
    "priceRecommendation" to priceRecommendation
)

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
