package com.quantjumpstock.core.application.backtest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.quantjumpstock.core.domain.model.backtest.BacktestResult
import com.quantjumpstock.core.domain.model.backtest.BacktestStatus
import com.quantjumpstock.core.domain.model.backtest.BacktestType
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
    private val objectMapper: ObjectMapper,
    private val canonicalBacktestService: CanonicalBacktestService
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

        // Data Engine이 실제 결과를 직접 저장한 레코드 (backtestResultId로 조회)
        val backtestResultId = payload.get("backtestResultId")?.asLong()
        val actualResult = backtestResultId?.let { backtestResultRepository.findByIdWithTrades(it) }

        // RUNNING placeholder 확인
        val placeholder = backtestResultRepository.findByRequestId(requestId)

        if (placeholder != null && placeholder.status == BacktestStatus.RUNNING) {
            if (actualResult != null && actualResult.id != placeholder.id) {
                // Data Engine이 별도 레코드에 결과 저장한 경우 (증분 백테스트)
                // → RUNNING placeholder에 실제 결과 복사
                val userId = resolveUserId(payload.get("userId")?.asText())
                val updated = placeholder.copy(
                    userId = userId ?: actualResult.userId ?: placeholder.userId,
                    finalValue = actualResult.finalValue,
                    totalReturn = actualResult.totalReturn,
                    cagr = actualResult.cagr,
                    mdd = actualResult.mdd,
                    sharpeRatio = actualResult.sharpeRatio,
                    sortinoRatio = actualResult.sortinoRatio,
                    volatility = actualResult.volatility,
                    winRate = actualResult.winRate,
                    totalTrades = actualResult.totalTrades,
                    winningTrades = actualResult.winningTrades,
                    losingTrades = actualResult.losingTrades,
                    avgWin = actualResult.avgWin,
                    avgLoss = actualResult.avgLoss,
                    benchmarkReturn = actualResult.benchmarkReturn,
                    alpha = actualResult.alpha,
                    beta = actualResult.beta,
                    equityCurve = actualResult.equityCurve,
                    // SCRUM-330: 고급 지표 복사
                    profitFactor = actualResult.profitFactor,
                    expectancy = actualResult.expectancy,
                    kellyPercentage = actualResult.kellyPercentage,
                    riskRewardRatio = actualResult.riskRewardRatio,
                    calmarRatio = actualResult.calmarRatio,
                    totalCommission = actualResult.totalCommission,
                    totalSlippage = actualResult.totalSlippage,
                    totalTax = actualResult.totalTax,
                    netProfitAfterCosts = actualResult.netProfitAfterCosts,
                    avgHoldingPeriod = actualResult.avgHoldingPeriod,
                    bestTrade = actualResult.bestTrade,
                    worstTrade = actualResult.worstTrade,
                    maxConsecutiveWins = actualResult.maxConsecutiveWins,
                    maxConsecutiveLosses = actualResult.maxConsecutiveLosses,
                    stopLossCount = actualResult.stopLossCount,
                    takeProfitCount = actualResult.takeProfitCount,
                    trailingStopCount = actualResult.trailingStopCount,
                    riskSettings = actualResult.riskSettings,
                    positionSizing = actualResult.positionSizing,
                    tradingCosts = actualResult.tradingCosts,
                    status = BacktestStatus.COMPLETED,
                    completedAt = LocalDateTime.now()
                )
                val saved = backtestResultRepository.save(updated)
                logger.info("백테스트 RUNNING → COMPLETED (증분 결과 복사): requestId=$requestId, placeholder=${placeholder.id}, source=${actualResult.id}")

                // SCRUM-344: Canonical 백테스트 완료 시 전략 업데이트
                notifyCanonicalIfNeeded(saved)

                // 거래 내역도 복사
                if (actualResult.trades.isNotEmpty()) {
                    val copiedTrades = actualResult.trades.map { trade ->
                        trade.copy(id = null, backtestResultId = saved.id)
                    }
                    backtestTradeRepository.saveAll(copiedTrades)
                    logger.info("거래 내역 복사: ${copiedTrades.size} 건")
                }
                return
            }

            // Data Engine이 직접 저장한 결과가 없는 경우 (이벤트 payload에서 metrics 파싱)
            val metrics = payload.get("metrics") ?: payload
            val userId = resolveUserId(payload.get("userId")?.asText())
            val updated = placeholder.copy(
                userId = userId ?: placeholder.userId,
                finalValue = parseBigDecimal(metrics.get("finalValue")) ?: placeholder.finalValue,
                totalReturn = parseBigDecimal(metrics.get("totalReturn")) ?: placeholder.totalReturn,
                cagr = parseBigDecimal(metrics.get("cagr")) ?: placeholder.cagr,
                mdd = parseBigDecimal(metrics.get("mdd")) ?: placeholder.mdd,
                sharpeRatio = parseBigDecimalOrNull(metrics.get("sharpeRatio")),
                sortinoRatio = parseBigDecimalOrNull(metrics.get("sortinoRatio")),
                volatility = parseBigDecimalOrNull(metrics.get("volatility")),
                winRate = parseBigDecimalOrNull(metrics.get("winRate")),
                totalTrades = metrics.get("totalTrades")?.asInt() ?: placeholder.totalTrades,
                winningTrades = metrics.get("winningTrades")?.asInt() ?: placeholder.winningTrades,
                losingTrades = metrics.get("losingTrades")?.asInt() ?: placeholder.losingTrades,
                avgWin = parseBigDecimalOrNull(metrics.get("avgWin")),
                avgLoss = parseBigDecimalOrNull(metrics.get("avgLoss")),
                // SCRUM-330: Advanced Risk Metrics
                profitFactor = parseBigDecimalOrNull(metrics.get("profitFactor")),
                expectancy = parseBigDecimalOrNull(metrics.get("expectancy")),
                kellyPercentage = parseBigDecimalOrNull(metrics.get("kellyPercentage")),
                riskRewardRatio = parseBigDecimalOrNull(metrics.get("riskRewardRatio")),
                calmarRatio = parseBigDecimalOrNull(metrics.get("calmarRatio")),
                totalCommission = parseBigDecimalOrNull(metrics.get("totalCommission")),
                totalSlippage = parseBigDecimalOrNull(metrics.get("totalSlippage")),
                totalTax = parseBigDecimalOrNull(metrics.get("totalTax")),
                netProfitAfterCosts = parseBigDecimalOrNull(metrics.get("netProfitAfterCosts")),
                avgHoldingPeriod = parseBigDecimalOrNull(metrics.get("avgHoldingPeriod")),
                bestTrade = parseBigDecimalOrNull(metrics.get("bestTrade")),
                worstTrade = parseBigDecimalOrNull(metrics.get("worstTrade")),
                maxConsecutiveWins = metrics.get("maxConsecutiveWins")?.asInt(),
                maxConsecutiveLosses = metrics.get("maxConsecutiveLosses")?.asInt(),
                stopLossCount = metrics.get("stopLossCount")?.asInt() ?: 0,
                takeProfitCount = metrics.get("takeProfitCount")?.asInt() ?: 0,
                trailingStopCount = metrics.get("trailingStopCount")?.asInt() ?: 0,
                riskSettings = payload.get("riskSettings")?.toString(),
                positionSizing = payload.get("positionSizing")?.toString(),
                tradingCosts = payload.get("tradingCosts")?.toString(),
                benchmarkReturn = parseBigDecimalOrNull(metrics.get("benchmarkReturn")),
                alpha = parseBigDecimalOrNull(metrics.get("alpha")),
                beta = parseBigDecimalOrNull(metrics.get("beta")),
                equityCurve = payload.get("equityCurve")?.toString() ?: placeholder.equityCurve,
                status = BacktestStatus.COMPLETED,
                completedAt = LocalDateTime.now()
            )
            val saved2 = backtestResultRepository.save(updated)
            logger.info("백테스트 RUNNING → COMPLETED 업데이트: requestId=$requestId, id=${placeholder.id}")

            // SCRUM-344: Canonical 백테스트 완료 시 전략 업데이트
            notifyCanonicalIfNeeded(saved2)

            // 이벤트에 trades가 있으면 저장
            val trades = payload.get("trades")
            if (trades != null && trades.isArray) {
                saveTrades(updated, trades)
            }
            return
        }

        if (placeholder != null) {
            logger.info("백테스트 결과 이미 존재 (Data Engine 직접 저장): requestId=$requestId, id=${placeholder.id}")
            val userId = resolveUserId(payload.get("userId")?.asText())
            if (userId != null && placeholder.userId == null) {
                val updated = placeholder.copy(userId = userId)
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
            // SCRUM-330: Advanced Risk Metrics
            profitFactor = parseBigDecimalOrNull(metrics.get("profitFactor")),
            expectancy = parseBigDecimalOrNull(metrics.get("expectancy")),
            kellyPercentage = parseBigDecimalOrNull(metrics.get("kellyPercentage")),
            riskRewardRatio = parseBigDecimalOrNull(metrics.get("riskRewardRatio")),
            calmarRatio = parseBigDecimalOrNull(metrics.get("calmarRatio")),
            totalCommission = parseBigDecimalOrNull(metrics.get("totalCommission")),
            totalSlippage = parseBigDecimalOrNull(metrics.get("totalSlippage")),
            totalTax = parseBigDecimalOrNull(metrics.get("totalTax")),
            netProfitAfterCosts = parseBigDecimalOrNull(metrics.get("netProfitAfterCosts")),
            avgHoldingPeriod = parseBigDecimalOrNull(metrics.get("avgHoldingPeriod")),
            bestTrade = parseBigDecimalOrNull(metrics.get("bestTrade")),
            worstTrade = parseBigDecimalOrNull(metrics.get("worstTrade")),
            maxConsecutiveWins = metrics.get("maxConsecutiveWins")?.asInt(),
            maxConsecutiveLosses = metrics.get("maxConsecutiveLosses")?.asInt(),
            stopLossCount = metrics.get("stopLossCount")?.asInt() ?: 0,
            takeProfitCount = metrics.get("takeProfitCount")?.asInt() ?: 0,
            trailingStopCount = metrics.get("trailingStopCount")?.asInt() ?: 0,
            riskSettings = payload.get("riskSettings")?.toString(),
            positionSizing = payload.get("positionSizing")?.toString(),
            tradingCosts = payload.get("tradingCosts")?.toString(),
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

        // RUNNING placeholder가 이미 있으면 상태만 업데이트
        val existing = backtestResultRepository.findByRequestId(requestId)
        if (existing != null) {
            val updated = existing.copy(
                status = BacktestStatus.FAILED,
                errorMessage = errorMessage,
                completedAt = LocalDateTime.now()
            )
            backtestResultRepository.save(updated)
            logger.info("백테스트 실패 상태 업데이트 완료: requestId=$requestId, backtestId=${existing.id}, error=$errorMessage")
            return
        }

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
                    signalReason = trade.get("signalReason")?.asText(),
                    // SCRUM-330: Exit Reason & Cost Details
                    exitReason = trade.get("exitReason")?.asText(),
                    slippageAmount = parseBigDecimalOrNull(trade.get("slippageAmount")),
                    taxAmount = parseBigDecimalOrNull(trade.get("taxAmount")),
                    executionPrice = parseBigDecimalOrNull(trade.get("executionPrice"))
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
    // SCRUM-344: Canonical Backtest Hook
    // ============================================================================

    private fun notifyCanonicalIfNeeded(result: BacktestResult) {
        if (result.backtestType == BacktestType.CANONICAL && result.status == BacktestStatus.COMPLETED) {
            try {
                canonicalBacktestService.onCanonicalBacktestCompleted(result.strategyId, result.id!!)
            } catch (e: Exception) {
                logger.warn("Canonical 백테스트 완료 처리 실패: strategyId={}, backtestId={}", result.strategyId, result.id, e)
            }
        }
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
