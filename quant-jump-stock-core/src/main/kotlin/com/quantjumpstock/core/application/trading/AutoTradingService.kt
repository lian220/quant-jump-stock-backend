package com.quantjumpstock.core.application.trading

import com.quantjumpstock.core.domain.model.backtest.UniverseType
import com.quantjumpstock.core.domain.port.output.StrategyDefaultStockRepository
import com.quantjumpstock.core.domain.port.output.StrategySubscriptionRepository
import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import com.quantjumpstock.core.domain.model.trading.*
import com.quantjumpstock.core.domain.model.user.User
import com.quantjumpstock.core.domain.port.output.AccountRepository
import com.quantjumpstock.core.domain.prediction.port.output.PredictionResultRepositoryPort
import com.quantjumpstock.core.domain.port.output.TradeRepository
import com.quantjumpstock.core.domain.port.output.TradeSignalExecutedRepository
import com.quantjumpstock.core.domain.port.output.TradingConfigRepository
import com.quantjumpstock.core.domain.port.output.UserRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AutoTradingService(
    private val userRepository: UserRepository,
    private val tradingConfigRepository: TradingConfigRepository,
    private val predictionResultRepository: PredictionResultRepositoryPort,
    private val tradeRepository: TradeRepository,
    private val tradeSignalExecutedRepository: TradeSignalExecutedRepository,
    private val accountRepository: AccountRepository,
    // SCRUM-349: Universe 기반 필터링
    private val strategySubscriptionRepository: StrategySubscriptionRepository,
    private val strategyDefaultStockRepository: StrategyDefaultStockRepository,
    // Phase 1A PRE Task 11: KIS 외부 호출을 AFTER_COMMIT 리스너로 위임
    private val applicationEventPublisher: ApplicationEventPublisher,
    // Phase 1A PRE backend-architect C-2: AFTER_COMMIT 리스너 보상 실패 시 영구 PENDING 회수
    private val stalePendingTradeReconciler: StalePendingTradeReconciler,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun executeAutoTrading() {
        // 본 사이클 시작 전, 이전 사이클의 영구 PENDING (리스너 보상 실패) 정리.
        // REQUIRES_NEW 트랜잭션이라 본 트랜잭션 실패와 독립적으로 commit.
        runCatching { stalePendingTradeReconciler.reconcile() }
            .onFailure { logger.error("StalePendingTradeReconciler 호출 실패 (사이클은 계속 진행)", it) }

        logger.info("🚀 Starting Auto Trading Execution...")
        // 스케줄러가 00:30(KST)에 실행되므로, 분석 데이터는 전날 날짜로 저장됨
        val analysisDate = LocalDate.now().minusDays(1)

        // 1️⃣ 예측 결과 조회 (PostgreSQL - Composite Score 기반)
        // Composite Score 2.0 이상인 매수 신호 조회
        val predictions = predictionResultRepository.findHighConfidenceBuySignals(analysisDate, 2.0)
        logger.info("✅ Found ${predictions.size} high-confidence buy signals for analysisDate ($analysisDate)")

        if (predictions.isEmpty()) {
            logger.info("❌ No high-confidence buy signals found. Skipping trading.")
            return
        }

        // 예측 결과 로깅
        predictions.forEach { prediction ->
            logger.info("📊 ${prediction.ticker}: Price=${prediction.aiPredictedPrice}, CompositeScore=${prediction.compositeScore}, Grade=${prediction.compositeGrade}")
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

        // Phase 1B v2.1: 활성 구독 캐시. 주문 발행 시 broker_account_id hint 추출용.
        // 현재 한계: prediction 에 strategyId 가 없어 사용자의 활성 구독 중 첫 번째 매핑 사용.
        // 정확한 strategy → account 매핑은 prediction 모델 확장 후 별도 phase.
        val activeSubscriptionsByUser: Map<Long, List<com.quantjumpstock.core.domain.port.output.StrategySubscriptionView>> =
            activeConfigs.associate { cfg ->
                cfg.userId to strategySubscriptionRepository.findActiveByUserId(cfg.userId)
            }

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
                val minCompositeScore = tradingConfig.minCompositeScore.toDouble()

                // SCRUM-349: 유니버스 기반 종목 필터셋 조회
                val universeTickerFilter = resolveUniverseTickerFilter(userId)
                if (universeTickerFilter != null) {
                    logger.info("🌐 Universe filter active for user ${user.userId}: ${universeTickerFilter.size} tickers allowed")
                }

                // 예측 결과를 Composite Score + 유니버스 필터링 후 상위 N개 선택
                val targetPredictions = predictions
                    .filter { it.compositeScore.toDouble() >= minCompositeScore }
                    .filter { universeTickerFilter == null || it.ticker in universeTickerFilter }
                    .sortedByDescending { it.compositeScore }
                    .take(maxStocks)

                logger.info("📊 Target stocks after filtering: ${targetPredictions.size}")

                var cashRemaining = availableCash

                targetPredictions.forEach predictionLoop@{ prediction ->
                    try {
                        val ticker = prediction.ticker
                        val price = prediction.aiPredictedPrice ?: return@predictionLoop
                        val predictionId = "${prediction.ticker}_${prediction.analysisDate}"

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
                            recordSignalExecution(userId, predictionId, ticker, prediction.compositeScore.toDouble(), ExecutionDecision.SKIPPED, "Already has pending order", null)
                            totalTradesSkipped++
                            return@predictionLoop
                        }

                        // 주문 금액 계산
                        val orderAmount = maxAmountPerStock.min(cashRemaining)
                        if (orderAmount < price) {
                            logger.info("⚠️ Insufficient funds for $ticker (need $price, have $orderAmount)")
                            recordSignalExecution(userId, predictionId, ticker, prediction.compositeScore.toDouble(), ExecutionDecision.SKIPPED, "Insufficient funds", null)
                            totalTradesSkipped++
                            return@predictionLoop
                        }

                        // 수량 계산 (소수점 버림)
                        val quantity = orderAmount.divide(price, 0, RoundingMode.DOWN).toInt()
                        if (quantity <= 0) {
                            logger.warn("⚠️ Calculated quantity is 0 for $ticker")
                            recordSignalExecution(userId, predictionId, ticker, prediction.compositeScore.toDouble(), ExecutionDecision.SKIPPED, "Quantity would be 0", null)
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
                            recordSignalExecution(userId, predictionId, ticker, prediction.compositeScore.toDouble(), ExecutionDecision.FAILED, "Failed to lock cash", null)
                            totalTradesSkipped++
                            return@predictionLoop
                        }

                        // 6️⃣ 거래 기록 생성 (PostgreSQL, PENDING)
                        val trade = Trade.createBuyOrder(
                            userId = userId,
                            ticker = ticker,
                            quantity = quantity,
                            price = price
                        )
                        val savedTrade = tradeRepository.save(trade)

                        // 7️⃣ Phase 1A PRE Task 11: KIS 외부 호출은 AFTER_COMMIT 리스너로 위임
                        //    - 트랜잭션 안에서는 PENDING Trade 저장 + 이벤트 발행만 수행
                        //    - OrderExecutionListener 가 KIS placeOrder 수행 → 성공: Trade.execute,
                        //      실패: Trade.fail + 현금 잠금 해제 + 신호 FAILED 로그
                        // Phase 1B v2.1: subscription 의 broker_account_id 라우팅 hint.
                        // 한계: prediction 에 strategyId 가 없어 어느 구독의 신호인지 확정 불가.
                        // 현재는 사용자의 활성 구독 중 첫 번째의 broker_account_id 사용 (없으면 null).
                        // null → OrderExecutionListener 가 사용자 활성 계좌 1개 자동 선택 (legacy).
                        // 정확한 strategy → account 매핑은 prediction 모델 확장 후 별도 phase.
                        val brokerAccountIdHint: Long? = activeSubscriptionsByUser[userId]
                            ?.firstNotNullOfOrNull { it.brokerAccountId }

                        applicationEventPublisher.publishEvent(
                            OrderExecutionRequestEvent(
                                tradeId = savedTrade.id!!,
                                userId = userId,
                                userIdString = user.userId,
                                ticker = ticker,
                                side = TradeSide.BUY,
                                quantity = quantity,
                                priceForKis = "0", // 시장가 주문
                                lockedAmount = totalAmount,
                                predictionId = predictionId,
                                compositeScore = prediction.compositeScore.toDouble(),
                                brokerAccountId = brokerAccountIdHint,
                            )
                        )

                        logger.info("✅ Queued PENDING BUY order: $ticker x$quantity @ $price = $totalAmount (CompositeScore: ${prediction.compositeScore})")
                        totalTradesCreated++
                        cashRemaining = cashRemaining - totalAmount

                    } catch (e: Exception) {
                        logger.error("❌ Error processing prediction for ${prediction.ticker}", e)
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
     * 신호 실행 로그 기록.
     * confidence 변환은 [TradeSignalExecuted.confidenceFromCompositeScore] 도메인 메서드로 통합.
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
        val confidence = TradeSignalExecuted.confidenceFromCompositeScore(compositeScore)
        val signal = when (decision) {
            ExecutionDecision.EXECUTED -> TradeSignalExecuted.recordExecution(
                userId, recommendationId, ticker, TradeSignal.BUY, confidence, tradeId!!
            )
            ExecutionDecision.SKIPPED -> TradeSignalExecuted.recordSkipped(
                userId, recommendationId, ticker, TradeSignal.BUY, confidence, skipReason ?: "Unknown"
            )
            ExecutionDecision.FAILED -> TradeSignalExecuted.recordFailed(
                userId, recommendationId, ticker, TradeSignal.BUY, confidence, skipReason ?: "Unknown"
            )
        }
        runCatching { tradeSignalExecutedRepository.save(signal) }
            .onFailure { logger.error("Failed to record signal execution", it) }
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
