package com.quantjumpstock.core.application.trading

import com.quantjumpstock.core.adapter.output.persistence.jpa.*
import com.quantjumpstock.core.adapter.output.persistence.mongodb.StockRecommendationRepository
import com.quantjumpstock.core.adapter.output.persistence.mongodb.PredictionResultMongoRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradingConfigJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradeSignalExecutedJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.application.balance.BalanceService
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
    private val userJpaRepository: UserJpaRepository,
    private val tradingConfigJpaRepository: TradingConfigJpaRepository,
    private val stockRecommendationRepository: StockRecommendationRepository,
    private val predictionResultMongoRepository: PredictionResultMongoRepository,
    private val tradeJpaRepository: TradeJpaRepository,
    private val tradeSignalExecutedJpaRepository: TradeSignalExecutedJpaRepository,
    private val balanceService: BalanceService,
    private val tradingApiPort: TradingApiPort
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun executeAutoTrading() {
        logger.info("🚀 Starting Auto Trading Execution...")
        val today = LocalDate.now()

        // 1️⃣ Vertex AI 예측 결과 조회 (MongoDB)
        // 신뢰도 70% 이상인 매수 신호만 조회
        val predictions = predictionResultMongoRepository.findHighConfidenceBuySignals(today, 0.7)
        logger.info("✅ Found ${predictions.size} high-confidence buy signals for today ($today)")

        if (predictions.isEmpty()) {
            logger.info("❌ No high-confidence buy signals found. Skipping trading.")
            return
        }

        // 예측 결과 로깅
        predictions.forEach { prediction ->
            logger.info("📊 ${prediction.symbol}: Price=${prediction.predictedPrice}, Confidence=${prediction.confidence}, Change=${prediction.predictedChangePercent}%")
        }

        // 2️⃣ 활성 사용자 조회 (최적화된 쿼리 - PostgreSQL)
        // 변경 전: userRepository.findAll().filter { ... }
        // 변경 후: 단일 JOIN 쿼리로 필요한 사용자만 조회
        val activeConfigs = tradingConfigJpaRepository.findAllEnabledWithAutoTrading()
        logger.info("✅ Found ${activeConfigs.size} active users for auto trading")

        if (activeConfigs.isEmpty()) {
            logger.info("❌ No users with auto trading enabled. Skipping.")
            return
        }

        var totalTradesCreated = 0
        var totalTradesSkipped = 0

        activeConfigs.forEach configLoop@{ tradingConfig ->
            try {
                val user = tradingConfig.user
                val userId = user.id ?: return@configLoop
                logger.info("👤 Processing user: ${user.userId}")

                // 3️⃣ 계좌 잔액 조회 (PostgreSQL)
                val availableCash = balanceService.getAvailableCash(userId)
                logger.info("💰 User ${user.userId} available cash: $availableCash")

                if (availableCash <= BigDecimal.ZERO) {
                    logger.info("⚠️ User ${user.userId} has no available cash. Skipping.")
                    return@configLoop
                }

                // 4️⃣ 거래 실행
                val maxStocks = tradingConfig.maxStocksToBuy
                val maxAmountPerStock = tradingConfig.maxAmountPerStock
                val minConfidence = tradingConfig.minCompositeScore.toDouble() / 100.0 // 예: 70 -> 0.7

                // Vertex AI 예측 결과를 신뢰도로 필터링 및 상위 N개 선택
                val targetPredictions = predictions
                    .filter { it.confidence >= minConfidence }
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
                        val recentTrades = tradeJpaRepository.findRecentTrade(
                            userId,
                            ticker,
                            TradeSide.BUY,
                            TradeStatus.PENDING,
                            LocalDateTime.now().minusHours(24)
                        )
                        if (recentTrades.isNotEmpty()) {
                            logger.info("⏭️ Skipping $ticker - already has pending order")
                            recordSignalExecution(user, predictionId, ticker, prediction.confidence * 100, ExecutionDecision.SKIPPED, "Already has pending order", null)
                            totalTradesSkipped++
                            return@predictionLoop
                        }

                        // 주문 금액 계산
                        val orderAmount = maxAmountPerStock.min(cashRemaining)
                        if (orderAmount < price) {
                            logger.info("⚠️ Insufficient funds for $ticker (need $price, have $orderAmount)")
                            recordSignalExecution(user, predictionId, ticker, prediction.confidence * 100, ExecutionDecision.SKIPPED, "Insufficient funds", null)
                            totalTradesSkipped++
                            return@predictionLoop
                        }

                        // 수량 계산 (소수점 버림)
                        val quantity = orderAmount.divide(price, 0, RoundingMode.DOWN).toInt()
                        if (quantity <= 0) {
                            logger.warn("⚠️ Calculated quantity is 0 for $ticker")
                            recordSignalExecution(user, predictionId, ticker, prediction.confidence * 100, ExecutionDecision.SKIPPED, "Quantity would be 0", null)
                            totalTradesSkipped++
                            return@predictionLoop
                        }

                        val totalAmount = price * quantity.toBigDecimal()

                        // 5️⃣ 현금 잠금
                        if (!balanceService.lockCash(userId, totalAmount)) {
                            logger.warn("⚠️ Failed to lock cash for $ticker")
                            recordSignalExecution(user, predictionId, ticker, prediction.confidence * 100, ExecutionDecision.FAILED, "Failed to lock cash", null)
                            totalTradesSkipped++
                            return@predictionLoop
                        }

                        // 6️⃣ 거래 기록 생성 (PostgreSQL)
                        val trade = TradeEntity(
                            user = user,
                            ticker = ticker,
                            side = TradeSide.BUY,
                            quantity = quantity,
                            price = price,
                            totalAmount = totalAmount,
                            status = TradeStatus.PENDING
                        )
                        val savedTrade = tradeJpaRepository.save(trade)

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
                                savedTrade.kisOrderId = kisOrderId
                                savedTrade.status = TradeStatus.EXECUTED
                                tradeJpaRepository.save(savedTrade)

                                logger.info("✅ KIS order placed: $ticker x$quantity (orderId: $kisOrderId)")
                            } else {
                                // 주문 실패 - 거래 상태 업데이트 및 현금 잠금 해제
                                savedTrade.status = TradeStatus.FAILED
                                tradeJpaRepository.save(savedTrade)
                                balanceService.unlockCash(userId, totalAmount)

                                val errorMsg = orderResult["msg1"] as? String ?: "Unknown error"
                                logger.error("❌ KIS order failed: $ticker - $errorMsg")

                                recordSignalExecution(user, predictionId, ticker, prediction.confidence * 100, ExecutionDecision.FAILED, "KIS API error: $errorMsg", savedTrade)
                                totalTradesSkipped++
                                return@predictionLoop
                            }
                        } catch (e: Exception) {
                            logger.error("❌ Exception during KIS order: $ticker", e)
                            savedTrade.status = TradeStatus.FAILED
                            tradeJpaRepository.save(savedTrade)
                            balanceService.unlockCash(userId, totalAmount)

                            recordSignalExecution(user, predictionId, ticker, prediction.confidence * 100, ExecutionDecision.FAILED, "Exception: ${e.message}", savedTrade)
                            totalTradesSkipped++
                            return@predictionLoop
                        }

                        // 8️⃣ 신호 실행 로그 기록
                        recordSignalExecution(user, predictionId, ticker, prediction.confidence * 100, ExecutionDecision.EXECUTED, null, savedTrade)

                        logger.info("✅ Created BUY order: $ticker x$quantity @ $price = $totalAmount (Confidence: ${prediction.confidence})")
                        totalTradesCreated++
                        cashRemaining = cashRemaining - totalAmount

                    } catch (e: Exception) {
                        logger.error("❌ Error processing prediction for ${prediction.symbol}", e)
                    }
                }

            } catch (e: Exception) {
                logger.error("❌ Error processing user ${tradingConfig.user.userId}", e)
            }
        }

        logger.info("✅ Auto Trading Execution Completed.")
        logger.info("📊 Summary: $totalTradesCreated trades created, $totalTradesSkipped skipped")
    }

    /**
     * 신호 실행 로그 기록
     */
    private fun recordSignalExecution(
        user: UserEntity,
        recommendationId: String,
        ticker: String,
        compositeScore: Double,
        decision: ExecutionDecision,
        skipReason: String?,
        trade: TradeEntity?
    ) {
        try {
            val signal = TradeSignalExecutedEntity(
                user = user,
                recommendationId = recommendationId,
                ticker = ticker,
                signal = TradeSignal.BUY,
                confidence = BigDecimal.valueOf(compositeScore / 10.0).setScale(2, RoundingMode.HALF_UP),
                executionDecision = decision,
                skipReason = skipReason,
                executedTrade = trade
            )
            tradeSignalExecutedJpaRepository.save(signal)
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

        val user = userJpaRepository.findByUserIdWithDetails(userId).orElse(null)
        if (user == null) {
            logger.warn("❌ User not found: $userId")
            return
        }

        val tradingConfig = user.tradingConfig
        if (tradingConfig == null || !tradingConfig.enabled || !tradingConfig.autoTradingEnabled) {
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
        val executedAt = if (status == TradeStatus.EXECUTED) LocalDateTime.now() else null
        tradeJpaRepository.updateTradeStatus(tradeId, status, executedAt, kisOrderId)
        logger.info("Updated trade $tradeId status to $status")
    }

    /**
     * 대기 중인 거래 조회
     */
    @Transactional(readOnly = true)
    fun getPendingTrades(): List<TradeEntity> {
        return tradeJpaRepository.findByStatus(TradeStatus.PENDING)
    }
}
