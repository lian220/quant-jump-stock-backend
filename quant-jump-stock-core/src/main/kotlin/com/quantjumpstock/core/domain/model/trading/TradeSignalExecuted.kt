package com.quantjumpstock.core.domain.model.trading

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * TradeSignalExecuted 도메인 모델 - 거래 신호 실행 기록
 */
data class TradeSignalExecuted(
    val id: Long? = null,
    val userId: Long,
    val recommendationId: String,
    val ticker: String,
    val signal: TradeSignal,
    val confidence: BigDecimal,
    val executionDecision: ExecutionDecision,
    val skipReason: String? = null,
    val executedTradeId: Long? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(ticker.isNotBlank()) { "Ticker must not be blank" }
        require(recommendationId.isNotBlank()) { "RecommendationId must not be blank" }
        require(confidence >= BigDecimal.ZERO && confidence <= BigDecimal.TEN) {
            "Confidence must be between 0 and 10"
        }
    }

    /**
     * 실행 성공 여부
     */
    fun isExecuted(): Boolean = executionDecision == ExecutionDecision.EXECUTED

    /**
     * 건너뜀 여부
     */
    fun isSkipped(): Boolean = executionDecision == ExecutionDecision.SKIPPED

    /**
     * 실패 여부
     */
    fun isFailed(): Boolean = executionDecision == ExecutionDecision.FAILED

    companion object {
        /**
         * compositeScore (0~10 범위) → confidence (0.00~1.00) 정규화.
         * AutoTradingService 가 매 신호마다 동일하게 계산하던 변환 로직을 도메인으로 통합.
         *
         * 입력 검증: 0..10 범위 외 값은 도메인 invariant 위반으로 즉시 거절.
         * 잘못된 confidence 가 silently 저장되어 audit log 를 오염시키는 것 방지.
         */
        fun confidenceFromCompositeScore(compositeScore: Double): java.math.BigDecimal {
            require(compositeScore in 0.0..10.0) {
                "compositeScore must be in [0.0, 10.0], got $compositeScore"
            }
            return java.math.BigDecimal.valueOf(compositeScore)
                .divide(java.math.BigDecimal.TEN, 2, java.math.RoundingMode.HALF_UP)
        }

        /**
         * 실행 기록 생성
         */
        fun recordExecution(
            userId: Long,
            recommendationId: String,
            ticker: String,
            signal: TradeSignal,
            confidence: BigDecimal,
            tradeId: Long
        ): TradeSignalExecuted {
            return TradeSignalExecuted(
                userId = userId,
                recommendationId = recommendationId,
                ticker = ticker,
                signal = signal,
                confidence = confidence,
                executionDecision = ExecutionDecision.EXECUTED,
                executedTradeId = tradeId
            )
        }

        /**
         * 건너뜀 기록 생성
         */
        fun recordSkipped(
            userId: Long,
            recommendationId: String,
            ticker: String,
            signal: TradeSignal,
            confidence: BigDecimal,
            reason: String
        ): TradeSignalExecuted {
            return TradeSignalExecuted(
                userId = userId,
                recommendationId = recommendationId,
                ticker = ticker,
                signal = signal,
                confidence = confidence,
                executionDecision = ExecutionDecision.SKIPPED,
                skipReason = reason
            )
        }

        /**
         * 실패 기록 생성
         */
        fun recordFailed(
            userId: Long,
            recommendationId: String,
            ticker: String,
            signal: TradeSignal,
            confidence: BigDecimal,
            reason: String
        ): TradeSignalExecuted {
            return TradeSignalExecuted(
                userId = userId,
                recommendationId = recommendationId,
                ticker = ticker,
                signal = signal,
                confidence = confidence,
                executionDecision = ExecutionDecision.FAILED,
                skipReason = reason
            )
        }
    }
}
