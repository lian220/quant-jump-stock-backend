package com.quantjumpstock.core.application.backtest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.quantjumpstock.core.domain.model.backtest.BacktestResult
import com.quantjumpstock.core.domain.model.backtest.BacktestStatus
import com.quantjumpstock.core.domain.model.backtest.BacktestTrade
import com.quantjumpstock.core.domain.model.backtest.BacktestTradeSide
import com.quantjumpstock.core.domain.port.output.Benchmark
import com.quantjumpstock.core.domain.port.output.UserRepository
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
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 백테스트 완료 결과 저장
     * Data Engine이 직접 PostgreSQL에 저장하므로, 이미 존재하면 userId만 업데이트
     */
    @Transactional
    fun saveBacktestResult(payload: JsonNode) {
        val requestId = payload.get("requestId")?.asText() ?: throw IllegalArgumentException("requestId is required")
        val strategyId = payload.get("strategyId")?.asLong() ?: throw IllegalArgumentException("strategyId is required")

        logger.info("백테스트 결과 저장 시작: requestId=$requestId, strategyId=$strategyId")

        // Data Engine이 이미 직접 저장한 경우 중복 방지
        val existing = backtestResultRepository.findByRequestId(requestId)
        if (existing != null) {
            logger.info("백테스트 결과 이미 존재 (Data Engine 직접 저장): requestId=$requestId, id=${existing.id}")
            // userId가 누락된 경우 업데이트
            val userId = resolveUserId(payload.get("userId")?.asText())
            if (userId != null && existing.userId == null) {
                val updated = existing.copy(userId = userId)
                backtestResultRepository.save(updated)
                logger.info("userId 업데이트: requestId=$requestId, userId=$userId")
            }
            return
        }

        val userId = resolveUserId(payload.get("userId")?.asText())

        // 메트릭 파싱
        val metrics = payload.get("metrics") ?: payload

        val backtestResult = BacktestResult(
            requestId = requestId,
            strategyId = strategyId,
            userId = userId,
            startDate = parseDate(payload.get("startDate")?.asText())
                ?: throw IllegalArgumentException("startDate is required"),
            endDate = parseDate(payload.get("endDate")?.asText())
                ?: throw IllegalArgumentException("endDate is required"),
            initialCapital = parseBigDecimal(payload.get("initialCapital"))
                ?: throw IllegalArgumentException("initialCapital is required"),
            benchmark = payload.get("benchmark")?.asText() ?: Benchmark.DEFAULT_TICKER,
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
     * Data Engine에서 실패 이벤트에 startDate/endDate/initialCapital이 누락될 수 있으므로
     * 기본값으로 처리하여 반드시 DB에 실패 레코드를 남긴다.
     */
    @Transactional
    fun saveBacktestFailure(payload: JsonNode) {
        val requestId = payload.get("requestId")?.asText() ?: throw IllegalArgumentException("requestId is required")
        val strategyId = payload.get("strategyId")?.asLong() ?: throw IllegalArgumentException("strategyId is required")
        val errorMessage = payload.get("errorMessage")?.asText() ?: "Unknown error"

        logger.info("백테스트 실패 결과 저장 시작: requestId=$requestId, strategyId=$strategyId")

        val userId = resolveUserId(payload.get("userId")?.asText())

        val backtestResult = BacktestResult(
            requestId = requestId,
            strategyId = strategyId,
            userId = userId,
            startDate = parseDate(payload.get("startDate")?.asText()) ?: LocalDate.now(),
            endDate = parseDate(payload.get("endDate")?.asText()) ?: LocalDate.now(),
            initialCapital = parseBigDecimal(payload.get("initialCapital")) ?: BigDecimal.ZERO,
            benchmark = payload.get("benchmark")?.asText() ?: Benchmark.DEFAULT_TICKER,
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

    /**
     * userId 문자열을 DB PK(Long)로 변환
     * - 숫자 문자열이면 그대로 Long 변환
     * - 문자열(로그인 ID)이면 users 테이블에서 PK 조회
     */
    private fun resolveUserId(userIdStr: String?): Long? {
        if (userIdStr.isNullOrBlank()) return null
        // 숫자면 바로 반환
        userIdStr.toLongOrNull()?.let { return it }
        // 문자열 userId로 DB 조회
        return try {
            userRepository.findByUserId(userIdStr)?.id
        } catch (e: Exception) {
            logger.warn("userId 조회 실패: userId=$userIdStr, error=${e.message}")
            null
        }
    }
}
