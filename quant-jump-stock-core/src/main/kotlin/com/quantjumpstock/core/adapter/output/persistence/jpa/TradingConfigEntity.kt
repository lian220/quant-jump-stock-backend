package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp

@Entity
@Table(name = "trading_configs")
data class TradingConfigEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: UserEntity,

    @Column(nullable = false)
    val enabled: Boolean = false,

    @Column(name = "auto_trading_enabled", nullable = false)
    val autoTradingEnabled: Boolean = false,

    @Column(name = "min_composite_score", precision = 5, scale = 2)
    val minCompositeScore: BigDecimal = BigDecimal("2.0"),

    @Column(name = "max_stocks_to_buy")
    val maxStocksToBuy: Int = 5,

    @Column(name = "max_amount_per_stock", precision = 12, scale = 2)
    val maxAmountPerStock: BigDecimal = BigDecimal("10000.0"),

    @Column(name = "stop_loss_percent", precision = 5, scale = 2)
    val stopLossPercent: BigDecimal = BigDecimal("-7.0"),

    @Column(name = "take_profit_percent", precision = 5, scale = 2)
    val takeProfitPercent: BigDecimal = BigDecimal("5.0"),

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
