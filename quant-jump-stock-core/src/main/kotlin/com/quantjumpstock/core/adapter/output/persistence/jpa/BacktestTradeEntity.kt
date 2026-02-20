package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp

@Entity
@Table(name = "backtest_trades")
class BacktestTradeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "backtest_id", nullable = false)
    val backtestResult: BacktestResultEntity,

    @Column(name = "trade_date", nullable = false)
    val tradeDate: LocalDate,

    @Column(nullable = false, length = 20)
    val ticker: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val side: BacktestTradeSide,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false, precision = 15, scale = 4)
    val price: BigDecimal,

    @Column(nullable = false, precision = 15, scale = 2)
    val amount: BigDecimal,

    @Column(precision = 10, scale = 2)
    val commission: BigDecimal = BigDecimal.ZERO,

    // P&L (for SELL trades)
    @Column(precision = 15, scale = 2)
    val pnl: BigDecimal? = null,

    @Column(name = "pnl_percent", precision = 10, scale = 4)
    val pnlPercent: BigDecimal? = null,

    @Column(name = "holding_days")
    val holdingDays: Int? = null,

    @Column(name = "signal_reason", length = 255)
    val signalReason: String? = null,

    // SCRUM-330: Exit Reason & Cost Details
    @Column(name = "exit_reason", length = 20)
    val exitReason: String? = null,

    @Column(name = "slippage_amount", precision = 10, scale = 2)
    val slippageAmount: BigDecimal? = null,

    @Column(name = "tax_amount", precision = 10, scale = 2)
    val taxAmount: BigDecimal? = null,

    @Column(name = "execution_price", precision = 15, scale = 4)
    val executionPrice: BigDecimal? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class BacktestTradeSide {
    BUY,
    SELL
}
