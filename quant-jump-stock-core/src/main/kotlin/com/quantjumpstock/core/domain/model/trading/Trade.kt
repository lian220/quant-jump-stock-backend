package com.quantjumpstock.core.domain.model.trading

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Trade 도메인 모델 - 순수 Kotlin
 *
 * 특징:
 * - JPA 어노테이션 없음
 * - 비즈니스 로직 포함
 * - 불변 객체 (copy로 상태 변경)
 */
data class Trade(
    val id: Long? = null,
    val userId: Long,
    val ticker: String,
    val side: TradeSide,
    val quantity: Int,
    val price: BigDecimal,
    val totalAmount: BigDecimal,
    val commission: BigDecimal = BigDecimal.ZERO,
    val status: TradeStatus = TradeStatus.PENDING,
    val kisOrderId: String? = null,
    val executedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(ticker.isNotBlank()) { "Ticker must not be blank" }
        require(quantity > 0) { "Quantity must be positive" }
        require(price > BigDecimal.ZERO) { "Price must be positive" }
        require(totalAmount > BigDecimal.ZERO) { "Total amount must be positive" }
        require(commission >= BigDecimal.ZERO) { "Commission must be non-negative" }
    }

    /**
     * 거래 체결
     */
    fun execute(kisOrderId: String? = null): Trade {
        require(status == TradeStatus.PENDING) { "Only pending trades can be executed" }
        return copy(
            status = TradeStatus.EXECUTED,
            kisOrderId = kisOrderId,
            executedAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 거래 실패 처리
     */
    fun fail(): Trade {
        require(status == TradeStatus.PENDING) { "Only pending trades can fail" }
        return copy(
            status = TradeStatus.FAILED,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 거래 취소
     */
    fun cancel(): Trade {
        require(status == TradeStatus.PENDING) { "Only pending trades can be cancelled" }
        return copy(
            status = TradeStatus.CANCELLED,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 순거래금액 계산 (수수료 포함)
     */
    fun netAmount(): BigDecimal = when (side) {
        TradeSide.BUY -> totalAmount + commission
        TradeSide.SELL -> totalAmount - commission
    }

    /**
     * 매수 여부
     */
    fun isBuy(): Boolean = side == TradeSide.BUY

    /**
     * 매도 여부
     */
    fun isSell(): Boolean = side == TradeSide.SELL

    /**
     * 체결 완료 여부
     */
    fun isExecuted(): Boolean = status == TradeStatus.EXECUTED

    /**
     * 최종 상태 여부
     */
    fun isFinal(): Boolean = status.isFinal()

    companion object {
        /**
         * 매수 주문 생성
         */
        fun createBuyOrder(
            userId: Long,
            ticker: String,
            quantity: Int,
            price: BigDecimal,
            commission: BigDecimal = BigDecimal.ZERO
        ): Trade {
            val totalAmount = price.multiply(BigDecimal(quantity))
            return Trade(
                userId = userId,
                ticker = ticker,
                side = TradeSide.BUY,
                quantity = quantity,
                price = price,
                totalAmount = totalAmount,
                commission = commission
            )
        }

        /**
         * 매도 주문 생성
         */
        fun createSellOrder(
            userId: Long,
            ticker: String,
            quantity: Int,
            price: BigDecimal,
            commission: BigDecimal = BigDecimal.ZERO
        ): Trade {
            val totalAmount = price.multiply(BigDecimal(quantity))
            return Trade(
                userId = userId,
                ticker = ticker,
                side = TradeSide.SELL,
                quantity = quantity,
                price = price,
                totalAmount = totalAmount,
                commission = commission
            )
        }
    }
}
