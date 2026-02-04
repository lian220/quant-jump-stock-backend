package com.quantjumpstock.core.domain.model.trading

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Account (계좌 잔액) 도메인 모델 - 순수 Kotlin
 *
 * 특징:
 * - JPA 어노테이션 없음
 * - 잔액 검증 로직 포함
 * - 불변 객체 (copy로 상태 변경)
 */
data class Account(
    val id: Long? = null,
    val userId: Long,
    val cash: BigDecimal = BigDecimal.ZERO,
    val totalValue: BigDecimal = BigDecimal.ZERO,
    val lockedCash: BigDecimal = BigDecimal.ZERO,
    val version: Long = 0,  // Optimistic Locking용
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(cash >= BigDecimal.ZERO) { "Cash must be non-negative" }
        require(totalValue >= BigDecimal.ZERO) { "Total value must be non-negative" }
        require(lockedCash >= BigDecimal.ZERO) { "Locked cash must be non-negative" }
        require(lockedCash <= cash) { "Locked cash cannot exceed total cash" }
    }

    /**
     * 사용 가능한 현금 (총 현금 - 잠긴 현금)
     */
    fun availableCash(): BigDecimal = cash - lockedCash

    /**
     * 출금 가능 여부 확인
     */
    fun canWithdraw(amount: BigDecimal): Boolean {
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
        return availableCash() >= amount
    }

    /**
     * 주문 가능 여부 확인
     */
    fun canPlaceOrder(orderAmount: BigDecimal): Boolean {
        require(orderAmount > BigDecimal.ZERO) { "Order amount must be positive" }
        return availableCash() >= orderAmount
    }

    /**
     * 현금 입금
     */
    fun deposit(amount: BigDecimal): Account {
        require(amount > BigDecimal.ZERO) { "Deposit amount must be positive" }
        return copy(
            cash = cash + amount,
            totalValue = totalValue + amount,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 현금 출금
     */
    fun withdraw(amount: BigDecimal): Account {
        require(amount > BigDecimal.ZERO) { "Withdrawal amount must be positive" }
        require(canWithdraw(amount)) { "Insufficient available cash for withdrawal" }
        return copy(
            cash = cash - amount,
            totalValue = totalValue - amount,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 주문용 현금 잠금
     */
    fun lockCash(amount: BigDecimal): Account {
        require(amount > BigDecimal.ZERO) { "Lock amount must be positive" }
        require(canPlaceOrder(amount)) { "Insufficient available cash to lock" }
        return copy(
            lockedCash = lockedCash + amount,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 잠긴 현금 해제
     */
    fun unlockCash(amount: BigDecimal): Account {
        require(amount > BigDecimal.ZERO) { "Unlock amount must be positive" }
        require(lockedCash >= amount) { "Cannot unlock more than locked amount" }
        return copy(
            lockedCash = lockedCash - amount,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 매수 체결 처리 (잠긴 현금 → 주식으로 전환)
     */
    fun executeBuy(amount: BigDecimal): Account {
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
        require(lockedCash >= amount) { "Insufficient locked cash for buy execution" }
        return copy(
            cash = cash - amount,
            lockedCash = lockedCash - amount,
            // totalValue는 주식 평가액이 반영되므로 여기서는 그대로
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 매도 체결 처리 (주식 → 현금으로 전환)
     */
    fun executeSell(amount: BigDecimal): Account {
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
        return copy(
            cash = cash + amount,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 총 자산 가치 업데이트 (주식 평가액 반영)
     */
    fun updateTotalValue(stockValue: BigDecimal): Account {
        require(stockValue >= BigDecimal.ZERO) { "Stock value must be non-negative" }
        return copy(
            totalValue = cash + stockValue,
            updatedAt = LocalDateTime.now()
        )
    }

    companion object {
        /**
         * 새 계좌 생성
         */
        fun createNew(userId: Long, initialCash: BigDecimal = BigDecimal.ZERO): Account {
            return Account(
                userId = userId,
                cash = initialCash,
                totalValue = initialCash
            )
        }
    }
}
