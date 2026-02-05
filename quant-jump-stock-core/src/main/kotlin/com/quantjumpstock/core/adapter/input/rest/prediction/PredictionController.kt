package com.quantjumpstock.core.adapter.input.rest.prediction

import com.quantjumpstock.core.adapter.input.api.PredictionApi
import com.quantjumpstock.core.application.prediction.PredictionService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 예측 결과 조회 Controller (Input Adapter)
 *
 * HTTP 요청을 PredictionService로 전달하는 Adapter 역할만 수행.
 * 비즈니스 로직은 Application Service에서 처리.
 */
@RestController
@RequestMapping("/api/v1/predictions")
class PredictionController(
    private val predictionService: PredictionService
) : PredictionApi {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun getAllPredictions(days: Int): ResponseEntity<Map<String, Any>> {
        return try {
            val result = predictionService.getAllPredictions(days)

            ResponseEntity.ok(mapOf(
                "success" to result.success,
                "count" to result.count,
                "fromDate" to result.fromDate.toString(),
                "predictions" to result.predictions
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
            val result = predictionService.getLatestPredictions()

            ResponseEntity.ok(mapOf(
                "success" to result.success,
                "date" to result.fromDate.toString(),
                "count" to result.count,
                "predictions" to result.predictions
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
            val threshold = minConfidence ?: 0.7
            val result = predictionService.getBuySignals(threshold)

            ResponseEntity.ok(mapOf(
                "success" to result.success,
                "date" to result.date.toString(),
                "minConfidence" to result.minConfidence,
                "count" to result.count,
                "buySignals" to result.buySignals
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
            val result = predictionService.getPredictionsBySymbol(symbol, limit)

            ResponseEntity.ok(mapOf(
                "success" to result.success,
                "symbol" to result.symbol,
                "count" to result.count,
                "predictions" to result.predictions
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
            val result = predictionService.getPredictionsByDate(date)

            ResponseEntity.ok(mapOf(
                "success" to result.success,
                "date" to result.fromDate.toString(),
                "count" to result.count,
                "predictions" to result.predictions
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
            val result = predictionService.getPredictionStats(days)

            ResponseEntity.ok(mapOf(
                "success" to result.success,
                "period" to result.period,
                "stats" to result.stats
            ))
        } catch (e: Exception) {
            logger.error("❌ 통계 조회 실패", e)
            ResponseEntity.internalServerError().body(mapOf(
                "success" to false,
                "message" to "통계 조회 실패: ${e.message}"
            ))
        }
    }
}
