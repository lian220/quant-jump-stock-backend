package com.quantjumpstock.core.application.trading

import com.quantjumpstock.core.domain.model.trading.TradeSide
import com.quantjumpstock.core.domain.model.trading.TradeSignal
import com.quantjumpstock.core.domain.model.trading.TradeSignalExecuted
import com.quantjumpstock.core.domain.port.output.AccountRepository
import com.quantjumpstock.core.domain.port.output.TradeRepository
import com.quantjumpstock.core.domain.port.output.TradeSignalExecutedRepository
import com.quantjumpstock.core.domain.trading.port.output.TradingApiPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Phase 1A PRE Task 11 — OrderExecutionRequestEvent 컨슈머 (Option B 경량판).
 *
 * AutoTradingService 의 트랜잭션이 커밋된 직후 KIS 외부 호출을 수행한다.
 * @TransactionalEventListener(AFTER_COMMIT) 는 publish 트랜잭션이 성공적으로 commit 된
 * 후에만 호출되므로 "트랜잭션은 끝났지만 외부 I/O 는 진행 중" 인 안전 구간에서 동작한다.
 *
 * 본 리스너는 자체 트랜잭션(REQUIRES_NEW) 으로 후속 DB 쓰기를 수행한다 (Trade 상태 업데이트,
 * 현금 잠금 해제, 신호 로그) — publisher 의 원 트랜잭션은 이미 커밋되었으므로 이 트랜잭션은
 * 독립적이다.
 *
 * 보상 처리:
 *  - KIS 응답 실패 / 예외 발생 시 [OrderExecutionRequestEvent.lockedAmount] 만큼
 *    [com.quantjumpstock.core.domain.model.trading.Account.unlockCash] 로 잠금을 해제한다.
 *
 * 한계 (현 시점, in-process 단계):
 *  - 리스너 자체가 크래시하면 KIS 응답·잠금 해제 모두 유실될 수 있음.
 *  - 추후 Pub/Sub + idempotency key 기반으로 교체하여 at-least-once 전달 보장 가능.
 */
@Component
class OrderExecutionListener(
    private val tradingApiPort: TradingApiPort,
    private val tradeRepository: TradeRepository,
    private val accountRepository: AccountRepository,
    private val tradeSignalExecutedRepository: TradeSignalExecutedRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onOrderExecutionRequest(event: OrderExecutionRequestEvent) {
        logger.info("📨 OrderExecutionRequest received: tradeId=${event.tradeId} ${event.ticker} x${event.quantity}")

        val orderType = when (event.side) {
            TradeSide.BUY -> "BUY"
            TradeSide.SELL -> "SELL"
        }

        try {
            try {
                val result = tradingApiPort.placeOrder(
                    userId = event.userIdString,
                    ticker = event.ticker,
                    orderType = orderType,
                    quantity = event.quantity,
                    price = event.priceForKis
                )

                val rtCd = result["rt_cd"] as? String
                if (rtCd == "0") {
                    val kisOrderId = (result["output"] as? Map<*, *>)?.get("KRX_FWDG_ORD_ORGNO") as? String
                    handleSuccess(event, kisOrderId)
                } else {
                    val errorMsg = result["msg1"] as? String ?: "Unknown error"
                    logger.error("❌ KIS order failed: ${event.ticker} - $errorMsg")
                    handleFailure(event, "KIS API error: $errorMsg")
                }
            } catch (e: Exception) {
                logger.error("❌ Exception during KIS order: ${event.ticker}", e)
                handleFailure(event, "Exception: ${e.message}")
            }
        } catch (uncaught: Throwable) {
            // 보상 자체(handleFailure 의 trade.fail() / unlockCash) 가 실패한 경우의 마지막 안전망.
            // StalePendingTradeReconciler 가 다음 자동매매 사이클에서 fail() 만 회수하므로
            // lockedCash 자동 unlock 은 운영자 수동 조치가 필요함을 critical log 로 노출.
            logger.error(
                "CRITICAL: OrderExecutionListener 보상 자체 실패. tradeId={} userId={} " +
                    "lockedAmount={} ticker={}. 수동 보상 필요: Trade fail + Account unlockCash.",
                event.tradeId, event.userId, event.lockedAmount, event.ticker, uncaught,
            )
            throw uncaught
        }
    }

    private fun handleSuccess(event: OrderExecutionRequestEvent, kisOrderId: String?) {
        val trade = tradeRepository.findById(event.tradeId)
        if (trade == null) {
            logger.error("Trade not found for execution: tradeId=${event.tradeId}")
            return
        }
        val executed = trade.execute(kisOrderId)
        tradeRepository.save(executed)

        recordSignal(event, executed = true, reason = null)
        logger.info("✅ KIS order executed: ${event.ticker} x${event.quantity} (kisOrderId=$kisOrderId)")
    }

    private fun handleFailure(event: OrderExecutionRequestEvent, reason: String) {
        val trade = tradeRepository.findById(event.tradeId)
        if (trade != null) {
            tradeRepository.save(trade.fail())
        } else {
            logger.error("Trade not found for failure handling: tradeId=${event.tradeId}")
        }

        releaseLockedCash(event)
        recordSignal(event, executed = false, reason = reason)
    }

    private fun releaseLockedCash(event: OrderExecutionRequestEvent) {
        val account = accountRepository.findByUserId(event.userId)
        if (account == null) {
            logger.error("Account not found for cash release: userId=${event.userId}")
            return
        }
        accountRepository.save(account.unlockCash(event.lockedAmount))
        logger.info("💰 Released locked cash: userId=${event.userId} amount=${event.lockedAmount}")
    }

    private fun recordSignal(event: OrderExecutionRequestEvent, executed: Boolean, reason: String?) {
        val confidence = TradeSignalExecuted.confidenceFromCompositeScore(event.compositeScore)
        val signalType = when (event.side) {
            TradeSide.BUY -> TradeSignal.BUY
            TradeSide.SELL -> TradeSignal.SELL
        }
        val signal = if (executed) {
            TradeSignalExecuted.recordExecution(
                userId = event.userId,
                recommendationId = event.predictionId,
                ticker = event.ticker,
                signal = signalType,
                confidence = confidence,
                tradeId = event.tradeId
            )
        } else {
            TradeSignalExecuted.recordFailed(
                userId = event.userId,
                recommendationId = event.predictionId,
                ticker = event.ticker,
                signal = signalType,
                confidence = confidence,
                reason = reason ?: "Unknown"
            )
        }
        try {
            tradeSignalExecutedRepository.save(signal)
        } catch (e: Exception) {
            logger.error("Failed to record signal execution", e)
        }
    }
}
