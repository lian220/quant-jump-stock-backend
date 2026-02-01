package com.quantjumpstock.core.adapter.input.rest.prediction

import com.quantjumpstock.core.adapter.input.api.PredictionApi
import com.quantjumpstock.core.adapter.output.persistence.mongodb.PredictionResultMongoRepository
import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 예측 결과 조회 Controller
 */
@RestController
@RequestMapping("/api/v1/predictions")
class PredictionController(
    private val predictionRepository: PredictionResultMongoRepository
) : PredictionApi {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun getAllPredictions(days: Int): ResponseEntity<Map<String, Any>> {
        return try {
            val fromDate = LocalDate.now().minusDays(days.toLong())
            val predictions = predictionRepository.findRecentPredictions(fromDate)

            ResponseEntity.ok(mapOf(
                "success" to true,
                "count" to predictions.size,
                "fromDate" to fromDate.toString(),
                "predictions" to predictions
            ))
        } catch (e: Exception) {
            logger.error("❌ 예측 결과 조회 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "예측 결과 조회 실패: ${e.message}"
            ))
        }
    }

    override fun getLatestPredictions(): ResponseEntity<Map<String, Any>> {
        return try {
            val today = LocalDate.now()
            val predictions = predictionRepository.findByDate(today)

            ResponseEntity.ok(mapOf(
                "success" to true,
                "date" to today.toString(),
                "count" to predictions.size,
                "predictions" to predictions.sortedByDescending { it.confidence }
            ))
        } catch (e: Exception) {
            logger.error("❌ 최신 예측 조회 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "최신 예측 조회 실패: ${e.message}"
            ))
        }
    }

    override fun getBuySignals(minConfidence: Double?): ResponseEntity<Map<String, Any>> {
        return try {
            val today = LocalDate.now()
            val threshold = minConfidence ?: 0.7

            val buySignals = predictionRepository.findHighConfidenceBuySignals(today, threshold)

            ResponseEntity.ok(mapOf(
                "success" to true,
                "date" to today.toString(),
                "minConfidence" to threshold,
                "count" to buySignals.size,
                "buySignals" to buySignals.sortedByDescending { it.confidence }
            ))
        } catch (e: Exception) {
            logger.error("❌ 매수 신호 조회 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "매수 신호 조회 실패: ${e.message}"
            ))
        }
    }

    override fun getPredictionsBySymbol(symbol: String, limit: Int): ResponseEntity<Map<String, Any>> {
        return try {
            val predictions = predictionRepository.findBySymbolOrderByDateDesc(symbol)
                .take(limit)

            ResponseEntity.ok(mapOf(
                "success" to true,
                "symbol" to symbol,
                "count" to predictions.size,
                "predictions" to predictions
            ))
        } catch (e: Exception) {
            logger.error("❌ 종목 예측 조회 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "종목 예측 조회 실패: ${e.message}"
            ))
        }
    }

    override fun getPredictionsByDate(date: LocalDate): ResponseEntity<Map<String, Any>> {
        return try {
            val predictions = predictionRepository.findByDate(date)

            ResponseEntity.ok(mapOf(
                "success" to true,
                "date" to date.toString(),
                "count" to predictions.size,
                "predictions" to predictions.sortedByDescending { it.confidence }
            ))
        } catch (e: Exception) {
            logger.error("❌ 날짜별 예측 조회 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "날짜별 예측 조회 실패: ${e.message}"
            ))
        }
    }

    override fun getPredictionStats(days: Int): ResponseEntity<Map<String, Any>> {
        return try {
            val fromDate = LocalDate.now().minusDays(days.toLong())
            val predictions = predictionRepository.findRecentPredictions(fromDate)

            val stats = calculateStats(predictions)

            ResponseEntity.ok(mapOf(
                "success" to true,
                "period" to "${fromDate} ~ ${LocalDate.now()}",
                "stats" to stats
            ))
        } catch (e: Exception) {
            logger.error("❌ 통계 조회 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "통계 조회 실패: ${e.message}"
            ))
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
