package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "strategy_signals")
class StrategySignalEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id", nullable = false)
    val strategy: StrategyEntity,

    @Column(name = "signal_date", nullable = false)
    val signalDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 20)
    val signalType: SignalType,

    @Column(length = 20)
    val ticker: String? = null,

    @Column(name = "target_weight", precision = 5, scale = 2)
    val targetWeight: BigDecimal? = null,

    @Column(columnDefinition = "TEXT")
    val reason: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions_snapshot", columnDefinition = "jsonb")
    val conditionsSnapshot: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class SignalType {
    BUY,
    SELL,
    REBALANCE,
    HOLD
}
