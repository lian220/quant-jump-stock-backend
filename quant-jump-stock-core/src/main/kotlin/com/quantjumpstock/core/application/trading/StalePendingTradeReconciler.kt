package com.quantjumpstock.core.application.trading

import com.quantjumpstock.core.domain.model.trading.Trade
import com.quantjumpstock.core.domain.port.output.TradeRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.TransactionDefinition
import java.time.Duration
import java.time.LocalDateTime

/**
 * Stale PENDING Trade 안전망 (Phase 1A PRE backend-architect C-2 + CodeRabbit #6 반영).
 *
 * **배경**: [OrderExecutionListener] 는 publisher 트랜잭션이 commit 된 *후* (AFTER_COMMIT)
 * 실행되므로 publisher 의 PENDING [Trade] 는 이미 DB 에 영구 저장된 상태다. 만약 리스너
 * 자체가 (a) JVM crash, (b) DB 일시 장애, (c) 보상 트랜잭션 자체 예외 로 인해
 * 정상적으로 `Trade.fail()` 또는 `Trade.execute()` 처리에 실패하면, **PENDING 상태가
 * 영구 고착**되고 사용자의 `lockedCash` 는 해제되지 않는다.
 *
 * **본 Reconciler 의 책임 (좁게 정의)**:
 * - `updatedAt` 이 [staleThreshold] 이전인 PENDING trade 를 찾아 `fail()` 로 마킹.
 * - `lockedCash` 자동 보상은 의도적으로 **수행하지 않음** (보수적 안전망).
 *
 * **트랜잭션 격리 (CodeRabbit #6)**: 각 trade 의 `fail()` 저장은 **per-trade REQUIRES_NEW**
 * 로 독립 commit. 한 trade 실패가 다른 trade 의 회수를 롤백시키지 않음.
 * `[TransactionTemplate]` + `PROPAGATION_REQUIRES_NEW` 사용 (별도 빈 분리 없이 self-bean
 * AOP 우회).
 *
 * **트리거**: [com.quantjumpstock.core.application.trading.AutoTradingService.executeAutoTrading]
 * 사이클 시작 시 호출 (별도 Cloud Scheduler 잡 추가 없이 기존 트리거 재사용).
 *
 * **향후 확장 (Phase 1B)**:
 * - `Trade` 에 `lockedAmount` 컬럼 추가 → 자동 unlock 가능.
 * - 또는 별도 `compensation_outbox` 테이블 + Pub/Sub 으로 idempotent 보상.
 */
@Component
class StalePendingTradeReconciler(
    private val tradeRepository: TradeRepository,
    txManager: PlatformTransactionManager,
    @Value("\${app.autotrading.stale-pending-threshold-minutes:5}")
    private val stalePendingThresholdMinutes: Long,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val perTradeTx = TransactionTemplate(txManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    /**
     * Stale PENDING trade 를 fail() 로 강제 전환.
     *
     * **트랜잭션 경계 없음** — 조회는 본 메서드, 각 trade 의 `fail()` 저장은 per-trade
     * REQUIRES_NEW. 한 trade 실패가 전체를 롤백시키지 않음.
     *
     * @return 성공적으로 회수한 trade 수 (실패 건은 critical log 로 노출)
     */
    fun reconcile(): Int {
        val threshold = LocalDateTime.now().minus(Duration.ofMinutes(stalePendingThresholdMinutes))
        val stale = tradeRepository.findStalePending(threshold)
        if (stale.isEmpty()) return 0

        var failed = 0
        for (trade in stale) {
            try {
                reconcileOne(trade)
                failed++
                // 운영자 수동 조치를 유도하는 critical log. lockedCash 자동 unlock 은 고의적으로 안 함.
                logger.warn(
                    "STALE_PENDING_TRADE_RECONCILED: tradeId={} userId={} ticker={} totalAmount={} " +
                        "(updatedAt={}). Manual lockedCash unlock review required.",
                    trade.id, trade.userId, trade.ticker, trade.totalAmount, trade.updatedAt,
                )
            } catch (ex: Exception) {
                logger.error("Stale PENDING trade fail() 실패: tradeId=${trade.id}", ex)
            }
        }
        return failed
    }

    /**
     * 단일 trade 의 fail() 저장. REQUIRES_NEW 로 독립 commit.
     */
    private fun reconcileOne(trade: Trade) {
        perTradeTx.executeWithoutResult {
            tradeRepository.save(trade.fail())
        }
    }
}
