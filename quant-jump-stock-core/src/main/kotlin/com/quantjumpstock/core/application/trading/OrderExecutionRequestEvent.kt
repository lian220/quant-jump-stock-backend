package com.quantjumpstock.core.application.trading

import com.quantjumpstock.core.domain.model.trading.TradeSide
import java.math.BigDecimal
import java.time.Instant

/**
 * 자동매매 주문 실행 요청 이벤트 — Phase 1A PRE Task 11 (Option B 경량판).
 *
 * AutoTradingService 가 PENDING Trade 를 저장한 직후 Spring ApplicationEventPublisher 로
 * 발행한다. @TransactionalEventListener(AFTER_COMMIT) 리스너가 본 이벤트를 받아 KIS 외부
 * 호출을 수행하므로, 트랜잭션 경계와 외부 I/O 가 분리된다.
 *
 * 추후 신뢰성/내구성 요구가 강해지면 본 in-process 이벤트를 Pub/Sub 메시지로 교체할 수 있다
 * (필드 구성은 그대로 사용 가능).
 *
 * 보상 필드:
 *  - [lockedAmount] : 리스너 실패 시 [com.quantjumpstock.core.domain.model.trading.Account.unlockCash]
 *    로 현금 잠금을 해제하기 위한 금액. 락 시점의 trade.totalAmount 와 동일.
 *
 * 신호 감사 필드:
 *  - [predictionId] / [compositeScore] : 리스너가 [TradeSignalExecuted] 로그를 기록할 때 사용.
 */
data class OrderExecutionRequestEvent(
    val tradeId: Long,
    val userId: Long,
    val userIdString: String,
    val ticker: String,
    val side: TradeSide,
    val quantity: Int,
    val priceForKis: String,
    val lockedAmount: BigDecimal,
    val predictionId: String,
    val compositeScore: Double,
    val requestedAt: Instant = Instant.now(),
    /**
     * 본 주문을 실행할 broker account (Phase 1B v2.1).
     * null = legacy fallback (OrderExecutionListener 가 사용자 활성 계좌 자동 선택).
     * 향후 모든 흐름이 broker_account_id 명시로 전환되면 null 허용 제거 가능.
     */
    val brokerAccountId: Long? = null,
)
