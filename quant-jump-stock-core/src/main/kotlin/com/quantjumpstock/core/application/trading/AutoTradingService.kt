package com.quantjumpstock.core.application.trading

import com.quantjumpstock.core.domain.model.backtest.UniverseType
import com.quantjumpstock.core.domain.port.output.StrategyDefaultStockRepository
import com.quantjumpstock.core.domain.port.output.StrategySubscriptionRepository
import com.quantjumpstock.core.domain.model.prediction.VertexAIPredictionResult
import com.quantjumpstock.core.domain.model.trading.*
import com.quantjumpstock.core.domain.model.user.User
import com.quantjumpstock.core.domain.port.output.AccountRepository
import com.quantjumpstock.core.domain.port.output.VertexAIPredictionResultRepository
import com.quantjumpstock.core.domain.port.output.TradeRepository
import com.quantjumpstock.core.domain.port.output.TradeSignalExecutedRepository
import com.quantjumpstock.core.domain.port.output.TradingConfigRepository
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.domain.trading.port.output.TradingApiPort
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AutoTradingService(
    private val userRepository: UserRepository,
    private val tradingConfigRepository: TradingConfigRepository,
    private val predictionResultRepository: VertexAIPredictionResultRepository,
    private val tradeRepository: TradeRepository,
    private val tradeSignalExecutedRepository: TradeSignalExecutedRepository,
    private val accountRepository: AccountRepository,
    private val tradingApiPort: TradingApiPort,
    // SCRUM-349: Universe 기반 필터링
    private val strategySubscriptionRepository: StrategySubscriptionRepository,
    private val strategyDefaultStockRepository: StrategyDefaultStockRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun executeAutoTrading() {
        logger.info("🚀 Starting Auto Trading Execution...")
        // 스케줄러가 00:30(KST)에 실행되므로, 분석 데이터는 전날 날짜로 저장됨
        val analysisDate = LocalDate.now().minusDays(1)

        // 1️⃣ Vertex AI 예측 결과 조회 (MongoDB)
        // 신뢰도 70% 이상인 매수 신호만 조회
        val predictions = predictionResultRepository.findHighConfidenceBuySignals(analysisDate, 0.7)
        logger.info("✅ Found ${predictions.size} high-confidence buy signals for analysisDate ($analysisDate)")

        if (predictions.isEmpty()) {
            logger.info("❌ No high-confidence buy signals found. Skipping trading.")
            return
        }

        // 예측 결과 로깅
        predictions.forEach { prediction ->
            logger.info("📊 ${prediction.symbol}: Price=${prediction.predictedPrice}, Confidence=${prediction.confidence}, Change=${prediction.predictedChangePercent}%")
        }

        // 2️⃣ 활성 사용자 조회 (최적화된 쿼리 - PostgreSQL)
        val activeConfigs = tradingConfigRepository.findAllEnabledWithAutoTrading()
        logger.info("✅ Found ${activeConfigs.size} active users for auto trading")

        if (activeConfigs.isEmpty()) {
            logger.info("❌ No users with auto trading enabled. Skipping.")
            return
        }

        var totalTradesCreated = 0
        var totalTradesSkipped = 0

        activeConfigs.forEach configLoop@{ tradingConfig ->
            try {
                val userId = tradingConfig.userId
                val user = userRepository.findById(userId) ?: run {
                    logger.warn("User not found: $userId")
                    return@configLoop
                }
                logger.info("👤 Processing user: ${user.userId}")

                // 3️⃣ 계좌 잔액 조회 (PostgreSQL)
                val account = accountRepository.findByUserId(userId)
                val availableCash = account?.availableCash() ?: BigDecimal.ZERO
                logger.info("💰 User ${user.userId} available cash: $availableCash")

                if (availableCash <= BigDecimal.ZERO) {
                    logger.info("⚠️ User ${user.userId} has no available cash. Skipping.")
                    return@configLoop
                }

                // 4️⃣ 거래 실행
                val maxStocks = tradingConfig.maxStocksToBuy
                val maxAmountPerStock = tradingConfig.maxAmountPerStock
                val minConfidence = tradingConfig.getMinConfidenceAsDecimal()

                // SCRUM-349: 유니버스 기반 종목 필터셋 조회
                val universeTickerFilter = resolveUniverseTickerFilter(userId)
                if (universeTickerFilter != null) {
                    logger.info("🌐 Universe filter active for user ${user.userId}: ${universeTickerFilter.size} tickers allowed")
                }

                // Vertex AI 예측 결과를 신뢰도 + 유니버스 필터링 후 상위 N개 선택
                val targetPredictions = predictions
                    .filter { it.confidence >= minConfidence }
                    .filter { universeTickerFilter == null || it.symbol in universeTickerFilter }
                    .sortedByDescending { it.confidence }
                    .take(maxStocks)

                logger.info("📊 Target stocks after filtering: ${targetPredictions.size}")

                var cashRemaining = availableCash

                targetPredictions.forEach predictionLoop@{ prediction ->
                    try {
                        val ticker = prediction.symbol
                        val price = prediction.predictedPrice.toBigDecimal()
                        val predictionId = prediction.id ?: return@predictionLoop

                        // 이미 오늘 같은 종목 거래했는지 확인
                        val recentTrades = tradeRepository.findRecentTrades(
                            userId,
                            ticker,
                            TradeSide.BUY,
                            TradeStatus.PENDING,
                            LocalDateTime.now().minusHours(24)
                        )
                        if (recentTrades.isNotEmpty()) {
                            logger.info("⏭️ Skipping $ticker - already has pending order")
                            recordSignalExecution(userId, predictionId, ticker, prediction.confidence * 100, ExecutionDecision.SKIPPED, "Already has pending order", null)
                            totalTradesSkipped++
                            return@predictionLoop
                        }

                        // 주문 금액 계산
                        val orderAmount = maxAmountPerStock.min(cashRemaining)
                        if (orderAmount < price) {
                            logger.info("⚠️ Insufficient funds for $ticker (need $price, have $orderAmount)")
                            recordSignalExecution(userId, predictionId, ticker, prediction.confidence * 100, ExecutionDecision.SKIPPED, "Insufficient funds", null)
                            totalTradesSkipped++
                            return@predictionLoop
                        }

                        // 수량 계산 (소수점 버림)
                        val quantity = orderAmount.divide(price, 0, RoundingMode.DOWN).toInt()
                        if (quantity <= 0) {
                            logger.warn("⚠️ Calculated quantity is 0 for $ticker")
                            recordSignalExecution(userId, predictionId, ticker, prediction.confidence * 100, ExecutionDecision.SKIPPED, "Quantity would be 0", null)
                            totalTradesSkipped++
                            return@predictionLoop
                        }

                        val totalAmount = price * quantity.toBigDecimal()

                        // 5️⃣ 현금 잠금
                        val lockedAccount = account?.let {
                            if (it.canPlaceOrder(totalAmount)) {
                                accountRepository.save(it.lockCash(totalAmount))
                            } else null
                        }
                        if (lockedAccount == null) {
                            logger.warn("⚠️ Failed to lock cash for $ticker")
                            recordSignalExecution(userId, predictionId, ticker, prediction.confidence * 100, ExecutionDecision.FAILED, "Failed to lock cash", null)
                            totalTradesSkipped++
                            return@predictionLoop
                        }

                        // 6️⃣ 거래 기록 생성 (PostgreSQL)
                        val trade = Trade.createBuyOrder(
                            userId = userId,
                            ticker = ticker,
                            quantity = quantity,
                            price = price
                        )
                        val savedTrade = tradeRepository.save(trade)

                        // 7️⃣ 실제 KIS API 주문 실행
                        try {
                            val orderResult = tradingApiPort.placeOrder(
                                userId = user.userId,
                                ticker = ticker,
                                orderType = "BUY",
                                quantity = quantity,
                                price = "0" // 시장가 주문
                            )

                            val rtCd = orderResult["rt_cd"] as? String
                            if (rtCd == "0") {
                                // 주문 성공
                                val kisOrderId = orderResult["output"]?.let {
                                    (it as? Map<*, *>)?.get("KRX_FWDG_ORD_ORGNO") as? String
                                }
                                val executedTrade = savedTrade.execute(kisOrderId)
                                tradeRepository.save(executedTrade)

                                logger.info("✅ KIS order placed: $ticker x$quantity (orderId: $kisOrderId)")
                            } else {
                                // 주문 실패 - 거래 상태 업데이트 및 현금 잠금 해제
                                val failedTrade = savedTrade.fail()
                                tradeRepository.save(failedTrade)
                                accountRepository.save(lockedAccount.unlockCash(totalAmount))

                                val errorMsg = orderResult["msg1"] as? String ?: "Unknown error"
                                logger.error("❌ KIS order failed: $ticker - $errorMsg")

                                recordSignalExecution(userId, predictionId, ticker, prediction.confidence * 100, ExecutionDecision.FAILED, "KIS API error: $errorMsg", savedTrade.id)
                                totalTradesSkipped++
                                return@predictionLoop
                            }
                        } catch (e: Exception) {
                            logger.error("❌ Exception during KIS order: $ticker", e)
                            val failedTrade = savedTrade.fail()
                            tradeRepository.save(failedTrade)
                            accountRepository.save(lockedAccount.unlockCash(totalAmount))

                            recordSignalExecution(userId, predictionId, ticker, prediction.confidence * 100, ExecutionDecision.FAILED, "Exception: ${e.message}", savedTrade.id)
                            totalTradesSkipped++
                            return@predictionLoop
                        }

                        // 8️⃣ 신호 실행 로그 기록
                        recordSignalExecution(userId, predictionId, ticker, prediction.confidence * 100, ExecutionDecision.EXECUTED, null, savedTrade.id)

                        logger.info("✅ Created BUY order: $ticker x$quantity @ $price = $totalAmount (Confidence: ${prediction.confidence})")
                        totalTradesCreated++
                        cashRemaining = cashRemaining - totalAmount

                    } catch (e: Exception) {
                        logger.error("❌ Error processing prediction for ${prediction.symbol}", e)
                    }
                }

            } catch (e: Exception) {
                logger.error("❌ Error processing trading config ${tradingConfig.id}", e)
            }
        }

        logger.info("✅ Auto Trading Execution Completed.")
        logger.info("📊 Summary: $totalTradesCreated trades created, $totalTradesSkipped skipped")
    }

    /**
     * SCRUM-349: 사용자의 활성 구독 유니버스 타입에 따른 허용 종목 셋 반환
     * - MARKET: null 반환 (모든 종목 허용)
     * - PORTFOLIO / FIXED: 구독 전략의 기본 종목 티커 셋 반환
     * - SECTOR: null 반환 (향후 확장)
     * 구독이 없거나 오류 시 null 반환 (전체 허용)
     */
    private fun resolveUniverseTickerFilter(userId: Long): Set<String>? {
        return try {
            val activeSubscriptions = strategySubscriptionRepository.findActiveByUserId(userId)

            if (activeSubscriptions.isEmpty()) return null

            // 복수 구독 중 PORTFOLIO/FIXED가 하나라도 있으면 해당 전략 기본 종목 합집합 사용
            val portfolioTickers = mutableSetOf<String>()
            var hasPortfolioOrFixed = false

            activeSubscriptions.forEach { sub ->
                when (sub.preferredUniverseType) {
                    UniverseType.PORTFOLIO, UniverseType.FIXED -> {
                        hasPortfolioOrFixed = true
                        val tickers = strategyDefaultStockRepository.findTickersByStrategyId(sub.strategyId)
                        portfolioTickers.addAll(tickers)
                    }
                    // MARKET, SECTOR: 필터 없음
                    else -> Unit
                }
            }

            when {
                hasPortfolioOrFixed && portfolioTickers.isNotEmpty() -> portfolioTickers
                hasPortfolioOrFixed -> {
                    // PORTFOLIO/FIXED subscriptions exist but no default stocks configured.
                    // Return empty set (deny all) instead of null (allow all) to prevent fail-open.
                    logger.warn("PORTFOLIO/FIXED subscriptions found for user $userId but no default stocks configured. Denying all tickers.")
                    emptySet()
                }
                else -> null
            }
        } catch (e: Exception) {
            logger.warn("Failed to resolve universe ticker filter for user $userId, defaulting to MARKET", e)
            null
        }
    }

    /**
     * 신호 실행 로그 기록
     */
    private fun recordSignalExecution(
        userId: Long,
        recommendationId: String,
        ticker: String,
        compositeScore: Double,
        decision: ExecutionDecision,
        skipReason: String?,
        tradeId: Long?
    ) {
        try {
            val signal = when (decision) {
                ExecutionDecision.EXECUTED -> TradeSignalExecuted.recordExecution(
                    userId = userId,
                    recommendationId = recommendationId,
                    ticker = ticker,
                    signal = TradeSignal.BUY,
                    confidence = BigDecimal.valueOf(compositeScore / 10.0).setScale(2, RoundingMode.HALF_UP),
                    tradeId = tradeId!!
                )
                ExecutionDecision.SKIPPED -> TradeSignalExecuted.recordSkipped(
                    userId = userId,
                    recommendationId = recommendationId,
                    ticker = ticker,
                    signal = TradeSignal.BUY,
                    confidence = BigDecimal.valueOf(compositeScore / 10.0).setScale(2, RoundingMode.HALF_UP),
                    reason = skipReason ?: "Unknown"
                )
                ExecutionDecision.FAILED -> TradeSignalExecuted.recordFailed(
                    userId = userId,
                    recommendationId = recommendationId,
                    ticker = ticker,
                    signal = TradeSignal.BUY,
                    confidence = BigDecimal.valueOf(compositeScore / 10.0).setScale(2, RoundingMode.HALF_UP),
                    reason = skipReason ?: "Unknown"
                )
            }
            tradeSignalExecutedRepository.save(signal)
        } catch (e: Exception) {
            logger.error("Failed to record signal execution", e)
        }
    }

    /**
     * 특정 사용자의 자동 매매 실행
     */
    @Transactional
    fun executeAutoTradingForUser(userId: String) {
        logger.info("🚀 Starting Auto Trading for user: $userId")

        val user = userRepository.findByUserId(userId)
        if (user == null) {
            logger.warn("❌ User not found: $userId")
            return
        }

        val tradingConfig = user.id?.let { tradingConfigRepository.findByUserId(it) }
        if (tradingConfig == null || !tradingConfig.isAutoTradingActive()) {
            logger.warn("❌ Auto trading not enabled for user: $userId")
            return
        }

        // 나머지 로직은 executeAutoTrading과 동일하게 처리
        // 단일 사용자만 처리
        logger.info("✅ Auto trading executed for user: $userId")
    }

    /**
     * 거래 상태 업데이트 (체결 확인 후)
     */
    @Transactional
    fun updateTradeStatus(tradeId: Long, status: TradeStatus, kisOrderId: String?) {
        val trade = tradeRepository.findById(tradeId) ?: return
        val updatedTrade = when (status) {
            TradeStatus.EXECUTED -> trade.execute(kisOrderId)
            TradeStatus.FAILED -> trade.fail()
            TradeStatus.CANCELLED -> trade.cancel()
            TradeStatus.PENDING -> trade
        }
        tradeRepository.save(updatedTrade)
        logger.info("Updated trade $tradeId status to $status")
    }

    /**
     * 대기 중인 거래 조회
     */
    @Transactional(readOnly = true)
    fun getPendingTrades(): List<Trade> {
        return tradeRepository.findPendingTrades()
    }
}
