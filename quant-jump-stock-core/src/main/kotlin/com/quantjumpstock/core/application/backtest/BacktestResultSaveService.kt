package com.quantjumpstock.core.application.backtest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.quantjumpstock.core.domain.model.backtest.BacktestResult
import com.quantjumpstock.core.domain.model.backtest.BacktestStatus
import com.quantjumpstock.core.domain.model.backtest.BacktestTrade
import com.quantjumpstock.core.domain.model.backtest.BacktestTradeSide
import com.quantjumpstock.core.domain.port.output.BacktestResultRepository
import com.quantjumpstock.core.domain.port.output.BacktestTradeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 백테스트 결과 저장 서비스
 * Kafka Consumer에서 받은 백테스트 결과를 DB에 저장합니다.
 */
@Service
class BacktestResultSaveService(
    private val backtestResultRepository: BacktestResultRepository,
    private val backtestTradeRepository: BacktestTradeRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 백테스트 완료 결과 저장
     */
    @Transactional
    fun saveBacktestResult(payload: JsonNode) {
        val requestId = payload.get("requestId")?.asText() ?: throw IllegalArgumentException("requestId is required")
        val strategyId = payload.get("strategyId")?.asLong() ?: throw IllegalArgumentException("strategyId is required")

        logger.info("백테스트 결과 저장 시작: requestId=$requestId, strategyId=$strategyId")

        val userId = payload.get("userId")?.asText()?.toLongOrNull()

        // 메트릭 파싱
        val metrics = payload.get("metrics") ?: payload

        val backtestResult = BacktestResult(
            strategyId = strategyId,
            userId = userId,
            startDate = parseDate(payload.get("startDate")?.asText())
                ?: throw IllegalArgumentException("startDate is required"),
            endDate = parseDate(payload.get("endDate")?.asText())
                ?: throw IllegalArgumentException("endDate is required"),
            initialCapital = parseBigDecimal(payload.get("initialCapital"))
                ?: throw IllegalArgumentException("initialCapital is required"),
            benchmark = payload.get("benchmark")?.asText() ?: "KOSPI",
            finalValue = parseBigDecimal(metrics.get("finalValue")) ?: BigDecimal.ZERO,
            totalReturn = parseBigDecimal(metrics.get("totalReturn")) ?: BigDecimal.ZERO,
            cagr = parseBigDecimal(metrics.get("cagr")) ?: BigDecimal.ZERO,
            mdd = parseBigDecimal(metrics.get("mdd")) ?: BigDecimal.ZERO,
            sharpeRatio = parseBigDecimalOrNull(metrics.get("sharpeRatio")),
            sortinoRatio = parseBigDecimalOrNull(metrics.get("sortinoRatio")),
            volatility = parseBigDecimalOrNull(metrics.get("volatility")),
            winRate = parseBigDecimalOrNull(metrics.get("winRate")),
            totalTrades = metrics.get("totalTrades")?.asInt() ?: 0,
            winningTrades = metrics.get("winningTrades")?.asInt() ?: 0,
            losingTrades = metrics.get("losingTrades")?.asInt() ?: 0,
            avgWin = parseBigDecimalOrNull(metrics.get("avgWin")),
            avgLoss = parseBigDecimalOrNull(metrics.get("avgLoss")),
            benchmarkReturn = parseBigDecimalOrNull(metrics.get("benchmarkReturn")),
            alpha = parseBigDecimalOrNull(metrics.get("alpha")),
            beta = parseBigDecimalOrNull(metrics.get("beta")),
            equityCurve = payload.get("equityCurve")?.toString(),
            status = BacktestStatus.COMPLETED,
            completedAt = LocalDateTime.now()
        )

        val savedResult = backtestResultRepository.save(backtestResult)
        logger.info("백테스트 결과 저장 완료: id=${savedResult.id}")

        // 거래 내역 저장
        val trades = payload.get("trades")
        if (trades != null && trades.isArray) {
            saveTrades(savedResult, trades)
        }

        logger.info("백테스트 결과 저장 완료: requestId=$requestId, backtestId=${savedResult.id}")
    }

    /**
     * 백테스트 실패 결과 저장
     */
    @Transactional
    fun saveBacktestFailure(payload: JsonNode) {
        val requestId = payload.get("requestId")?.asText() ?: throw IllegalArgumentException("requestId is required")
        val strategyId = payload.get("strategyId")?.asLong() ?: throw IllegalArgumentException("strategyId is required")
        val errorMessage = payload.get("errorMessage")?.asText() ?: "Unknown error"

        logger.info("백테스트 실패 결과 저장 시작: requestId=$requestId, strategyId=$strategyId")

        val userId = payload.get("userId")?.asText()?.toLongOrNull()

        val backtestResult = BacktestResult(
            strategyId = strategyId,
            userId = userId,
            startDate = parseDate(payload.get("startDate")?.asText())
                ?: throw IllegalArgumentException("startDate is required"),
            endDate = parseDate(payload.get("endDate")?.asText())
                ?: throw IllegalArgumentException("endDate is required"),
            initialCapital = parseBigDecimal(payload.get("initialCapital"))
                ?: throw IllegalArgumentException("initialCapital is required"),
            benchmark = payload.get("benchmark")?.asText() ?: "KOSPI",
            finalValue = BigDecimal.ZERO,
            totalReturn = BigDecimal.ZERO,
            cagr = BigDecimal.ZERO,
            mdd = BigDecimal.ZERO,
            status = BacktestStatus.FAILED,
            errorMessage = errorMessage,
            completedAt = LocalDateTime.now()
        )

        val savedResult = backtestResultRepository.save(backtestResult)
        logger.info("백테스트 실패 결과 저장 완료: requestId=$requestId, backtestId=${savedResult.id}, error=$errorMessage")
    }

    /**
     * 거래 내역 일괄 저장
     */
    private fun saveTrades(backtestResult: BacktestResult, trades: JsonNode) {
        val tradeEntities = trades.mapNotNull { trade ->
            try {
                BacktestTrade(
                    backtestResultId = backtestResult.id,
                    tradeDate = parseDate(trade.get("tradeDate")?.asText()) ?: LocalDate.now(),
                    ticker = trade.get("ticker")?.asText() ?: "UNKNOWN",
                    side = parseTradeSide(trade.get("side")?.asText()),
                    quantity = trade.get("quantity")?.asInt() ?: 0,
                    price = parseBigDecimal(trade.get("price")) ?: BigDecimal.ZERO,
                    amount = parseBigDecimal(trade.get("amount")) ?: BigDecimal.ZERO,
                    commission = parseBigDecimal(trade.get("commission")) ?: BigDecimal.ZERO,
                    pnl = parseBigDecimalOrNull(trade.get("pnl")),
                    pnlPercent = parseBigDecimalOrNull(trade.get("pnlPercent")),
                    holdingDays = trade.get("holdingDays")?.asInt(),
                    signalReason = trade.get("signalReason")?.asText()
                )
            } catch (e: Exception) {
                logger.warn("거래 내역 파싱 실패: ${e.message}")
                null
            }
        }

        backtestTradeRepository.saveAll(tradeEntities)
        logger.info("거래 내역 저장 완료: ${tradeEntities.size} 건")
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private fun parseDate(dateStr: String?): LocalDate? {
        return try {
            dateStr?.let { LocalDate.parse(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseBigDecimal(node: JsonNode?): BigDecimal? {
        return try {
            when {
                node == null || node.isNull -> null
                node.isNumber -> node.decimalValue()
                node.isTextual -> BigDecimal(node.asText())
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseBigDecimalOrNull(node: JsonNode?): BigDecimal? {
        return parseBigDecimal(node)
    }

    private fun parseTradeSide(side: String?): BacktestTradeSide {
        return when (side?.uppercase()) {
            "BUY" -> BacktestTradeSide.BUY
            "SELL" -> BacktestTradeSide.SELL
            else -> throw IllegalArgumentException("Unknown trade side: $side")
        }
    }
}
