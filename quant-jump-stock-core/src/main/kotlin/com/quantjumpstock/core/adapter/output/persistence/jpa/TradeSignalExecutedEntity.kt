package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp

@Entity
@Table(name = "trade_signals_executed")
class TradeSignalExecutedEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    @Column(name = "recommendation_id", nullable = false, length = 100)
    val recommendationId: String,

    @Column(nullable = false, length = 10)
    val ticker: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val signal: TradeSignal,

    @Column(nullable = false, precision = 3, scale = 2)
    val confidence: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_decision", nullable = false, length = 20)
    val executionDecision: ExecutionDecision,

    @Column(name = "skip_reason", length = 255)
    val skipReason: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executed_trade_id")
    val executedTrade: TradeEntity? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class TradeSignal {
    BUY, SELL, HOLD
}

enum class ExecutionDecision {
    EXECUTED, SKIPPED, FAILED
}
