package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp

@Entity
@Table(
    name = "strategy_default_stocks",
    uniqueConstraints = [UniqueConstraint(columnNames = ["strategy_id", "stock_id"])]
)
class StrategyDefaultStockEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "strategy_id", nullable = false)
    val strategyId: Long,

    @Column(name = "stock_id", nullable = false)
    val stockId: Long,

    @Column(name = "target_weight", precision = 5, scale = 2, nullable = false)
    val targetWeight: BigDecimal = BigDecimal.ZERO,

    @Column(columnDefinition = "TEXT")
    val memo: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
