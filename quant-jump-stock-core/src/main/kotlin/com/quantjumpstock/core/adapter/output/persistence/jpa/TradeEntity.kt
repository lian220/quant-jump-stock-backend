package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp

@Entity
@Table(name = "trades")
class TradeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    @Column(nullable = false, length = 10)
    val ticker: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val side: TradeSide,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false, precision = 10, scale = 2)
    val price: BigDecimal,

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    val totalAmount: BigDecimal,

    @Column(precision = 10, scale = 2)
    val commission: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var status: TradeStatus = TradeStatus.PENDING,

    @Column(name = "kis_order_id", length = 100)
    var kisOrderId: String? = null,

    @Column(name = "executed_at")
    var executedAt: LocalDateTime? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class TradeSide {
    BUY, SELL
}

enum class TradeStatus {
    PENDING, EXECUTED, FAILED, CANCELLED
}
