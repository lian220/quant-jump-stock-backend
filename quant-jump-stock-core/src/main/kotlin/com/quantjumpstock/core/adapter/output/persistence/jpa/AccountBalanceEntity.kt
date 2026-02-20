package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp

@Entity
@Table(name = "account_balances")
class AccountBalanceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: UserEntity,

    @Column(nullable = false, precision = 15, scale = 2)
    var cash: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_value", nullable = false, precision = 15, scale = 2)
    var totalValue: BigDecimal = BigDecimal.ZERO,

    @Column(name = "locked_cash", precision = 15, scale = 2)
    var lockedCash: BigDecimal = BigDecimal.ZERO,

    @Version
    val version: Long = 0,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 사용 가능한 현금 (총 현금 - 잠긴 현금)
     */
    fun getAvailableCash(): BigDecimal = cash - lockedCash
}
