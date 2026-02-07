package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "portfolio_stocks",
    uniqueConstraints = [UniqueConstraint(columnNames = ["portfolio_id", "stock_id"])]
)
data class PortfolioStockEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "portfolio_id", nullable = false)
    val portfolioId: Long,

    @Column(name = "stock_id", nullable = false)
    val stockId: Long,

    @Column(name = "target_weight", precision = 5, scale = 2, nullable = false)
    val targetWeight: BigDecimal = BigDecimal.ZERO,

    @Column(name = "is_from_strategy", nullable = false)
    val isFromStrategy: Boolean = false,

    @Column(columnDefinition = "TEXT")
    val memo: String? = null,

    @Column(name = "added_at", nullable = false)
    val addedAt: LocalDateTime = LocalDateTime.now()
)
