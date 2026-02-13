package com.quantjumpstock.core.application.prediction

import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import com.quantjumpstock.core.domain.prediction.port.output.PredictionRepositoryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Prediction Application Service
 *
 * 예측 결과 조회 비즈니스 로직을 담당.
 * Port를 통해 Repository에 접근하여 Hexagonal Architecture 준수.
 */
@Service
class PredictionService(
    private val predictionRepository: PredictionRepositoryPort
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 최근 예측 결과 조회
     *
     * @param days 조회할 기간 (일 수)
     * @return 예측 결과 응답
     */
    fun getAllPredictions(days: Int): PredictionListResponse {
        val fromDate = LocalDate.now().minusDays(days.toLong())
        val predictions = predictionRepository.findRecentPredictions(fromDate)

        return PredictionListResponse(
            success = true,
            count = predictions.size,
            fromDate = fromDate,
            predictions = predictions
        )
    }

    /**
     * 오늘의 최신 예측 결과 조회
     *
     * @return 예측 결과 응답 (신뢰도 순 정렬)
     */
    fun getLatestPredictions(): PredictionListResponse {
        val today = LocalDate.now()
        val predictions = predictionRepository.findByDate(today)
            .sortedByDescending { it.confidence }

        return PredictionListResponse(
            success = true,
            count = predictions.size,
            fromDate = today,
            predictions = predictions
        )
    }

    /**
     * 매수 신호 조회
     *
     * ⚠️ 중요: 스케줄러가 23:05(KST)에 실행되어 전날 날짜로 데이터를 저장하므로,
     *         전날 날짜로 조회해야 함. (당일 조회 시 데이터 없음)
     *
     * @param minConfidence 최소 신뢰도 (기본값 0.7)
     *                     Composite Score 기준으로 변환: minConfidence × 7.5
     *                     예: 0.7 → 5.25점
     * @return 매수 신호 응답
     */
    fun getBuySignals(minConfidence: Double = 0.7): BuySignalsResponse {
        // ✅ 수정: 전날 날짜로 조회 (스케줄러가 23:05에 전날 날짜로 저장)
        val yesterday = LocalDate.now().minusDays(1)

        // Composite Score 기준으로 조회 (0.7 → 5.25점)
        // 기존 confidence(0~1)를 Composite Score(0~7.5) 기준으로 변환
        val minCompositeScore = minConfidence * 7.5

        val buySignals = predictionRepository.findHighConfidenceBuySignals(
            yesterday,
            minCompositeScore
        ).sortedByDescending { it.confidence }

        return BuySignalsResponse(
            success = true,
            date = yesterday,  // ✅ 응답에도 전날 날짜 표시
            minConfidence = minConfidence,
            count = buySignals.size,
            buySignals = buySignals
        )
    }

    /**
     * 특정 종목의 예측 결과 조회
     *
     * @param symbol 종목 심볼
     * @param limit 최대 조회 개수
     * @return 종목 예측 응답
     */
    fun getPredictionsBySymbol(symbol: String, limit: Int): SymbolPredictionsResponse {
        val predictions = predictionRepository.findBySymbolOrderByDateDesc(symbol)
            .take(limit)

        return SymbolPredictionsResponse(
            success = true,
            symbol = symbol,
            count = predictions.size,
            predictions = predictions
        )
    }

    /**
     * 특정 날짜의 예측 결과 조회
     *
     * @param date 조회 날짜
     * @return 예측 결과 응답
     */
    fun getPredictionsByDate(date: LocalDate): PredictionListResponse {
        val predictions = predictionRepository.findByDate(date)
            .sortedByDescending { it.confidence }

        return PredictionListResponse(
            success = true,
            count = predictions.size,
            fromDate = date,
            predictions = predictions
        )
    }

    /**
     * 예측 통계 조회
     *
     * @param days 통계 기간 (일 수)
     * @return 통계 응답
     */
    fun getPredictionStats(days: Int): PredictionStatsResponse {
        val fromDate = LocalDate.now().minusDays(days.toLong())
        val predictions = predictionRepository.findRecentPredictions(fromDate)

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

        val buyCount = predictions.count { it.signal == "BUY" }
        val sellCount = predictions.count { it.signal == "SELL" }
        val holdCount = predictions.count { it.signal == "HOLD" }

        val avgConfidence = predictions.map { it.confidence }.average()
        val highConfidenceCount = predictions.count { it.confidence >= 0.7 }

        return mapOf(
            "total" to predictions.size,
            "signals" to mapOf(
                "BUY" to buyCount,
                "SELL" to sellCount,
                "HOLD" to holdCount
            ),
            "signalRatio" to mapOf(
                "BUY" to String.format("%.1f%%", buyCount.toDouble() / predictions.size * 100),
                "SELL" to String.format("%.1f%%", sellCount.toDouble() / predictions.size * 100),
                "HOLD" to String.format("%.1f%%", holdCount.toDouble() / predictions.size * 100)
            ),
            "confidence" to mapOf(
                "average" to String.format("%.2f", avgConfidence),
                "high" to highConfidenceCount,
                "highRatio" to String.format("%.1f%%", highConfidenceCount.toDouble() / predictions.size * 100)
            ),
            "uniqueSymbols" to predictions.map { it.symbol }.distinct().size
        )
    }
}

/**
 * 예측 결과 목록 응답
 */
data class PredictionListResponse(
    val success: Boolean,
    val count: Int,
    val fromDate: LocalDate,
    val predictions: List<PredictionResult>
)

/**
 * 매수 신호 응답
 */
data class BuySignalsResponse(
    val success: Boolean,
    val date: LocalDate,
    val minConfidence: Double,
    val count: Int,
    val buySignals: List<PredictionResult>
)

/**
 * 종목별 예측 응답
 */
data class SymbolPredictionsResponse(
    val success: Boolean,
    val symbol: String,
    val count: Int,
    val predictions: List<PredictionResult>
)

/**
 * 예측 통계 응답
 */
data class PredictionStatsResponse(
    val success: Boolean,
    val period: String,
    val stats: Map<String, Any>
)
