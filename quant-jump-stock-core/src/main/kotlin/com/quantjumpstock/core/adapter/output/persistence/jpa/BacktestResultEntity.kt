package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.quantjumpstock.core.domain.port.output.Benchmark
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "backtest_results")
data class BacktestResultEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "request_id", unique = true, length = 36)
    val requestId: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id", nullable = false)
    val strategy: StrategyEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: UserEntity? = null,

    // Test Settings
    @Column(name = "start_date", nullable = false)
    val startDate: LocalDate,

    @Column(name = "end_date", nullable = false)
    val endDate: LocalDate,

    @Column(name = "initial_capital", nullable = false, precision = 15, scale = 2)
    val initialCapital: BigDecimal,

    @Column(length = 20)
    val benchmark: String = Benchmark.DEFAULT_TICKER,

    // SCRUM-337: 다중 벤치마크 (JSONB 배열)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benchmarks", columnDefinition = "jsonb")
    val benchmarks: String? = null,

    // Performance Metrics
    @Column(name = "final_value", nullable = false, precision = 15, scale = 2)
    val finalValue: BigDecimal,

    @Column(name = "total_return", nullable = false, precision = 10, scale = 4)
    val totalReturn: BigDecimal,

    @Column(nullable = false, precision = 10, scale = 4)
    val cagr: BigDecimal,

    @Column(nullable = false, precision = 10, scale = 4)
    val mdd: BigDecimal,

    @Column(name = "sharpe_ratio", precision = 10, scale = 4)
    val sharpeRatio: BigDecimal? = null,

    @Column(name = "sortino_ratio", precision = 10, scale = 4)
    val sortinoRatio: BigDecimal? = null,

    @Column(precision = 10, scale = 4)
    val volatility: BigDecimal? = null,

    @Column(name = "win_rate", precision = 5, scale = 2)
    val winRate: BigDecimal? = null,

    // Trade Statistics
    @Column(name = "total_trades")
    val totalTrades: Int = 0,

    @Column(name = "winning_trades")
    val winningTrades: Int = 0,

    @Column(name = "losing_trades")
    val losingTrades: Int = 0,

    @Column(name = "avg_win", precision = 10, scale = 4)
    val avgWin: BigDecimal? = null,

    @Column(name = "avg_loss", precision = 10, scale = 4)
    val avgLoss: BigDecimal? = null,

    // Benchmark Comparison
    @Column(name = "benchmark_return", precision = 10, scale = 4)
    val benchmarkReturn: BigDecimal? = null,

    @Column(precision = 10, scale = 4)
    val alpha: BigDecimal? = null,

    @Column(precision = 10, scale = 4)
    val beta: BigDecimal? = null,

    // Equity Curve (JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "equity_curve", columnDefinition = "jsonb")
    val equityCurve: String? = null,

    // SCRUM-330: Advanced Risk Metrics
    @Column(name = "profit_factor", precision = 10, scale = 4)
    val profitFactor: BigDecimal? = null,

    @Column(precision = 10, scale = 4)
    val expectancy: BigDecimal? = null,

    @Column(name = "kelly_percentage", precision = 10, scale = 4)
    val kellyPercentage: BigDecimal? = null,

    @Column(name = "risk_reward_ratio", precision = 10, scale = 4)
    val riskRewardRatio: BigDecimal? = null,

    @Column(name = "calmar_ratio", precision = 10, scale = 4)
    val calmarRatio: BigDecimal? = null,

    // SCRUM-330: Trading Cost Aggregates
    @Column(name = "total_commission", precision = 15, scale = 2)
    val totalCommission: BigDecimal? = null,

    @Column(name = "total_slippage", precision = 15, scale = 2)
    val totalSlippage: BigDecimal? = null,

    @Column(name = "total_tax", precision = 15, scale = 2)
    val totalTax: BigDecimal? = null,

    @Column(name = "net_profit_after_costs", precision = 15, scale = 2)
    val netProfitAfterCosts: BigDecimal? = null,

    // SCRUM-330: Trade Statistics
    @Column(name = "avg_holding_period", precision = 10, scale = 2)
    val avgHoldingPeriod: BigDecimal? = null,

    @Column(name = "best_trade", precision = 10, scale = 4)
    val bestTrade: BigDecimal? = null,

    @Column(name = "worst_trade", precision = 10, scale = 4)
    val worstTrade: BigDecimal? = null,

    @Column(name = "max_consecutive_wins")
    val maxConsecutiveWins: Int? = null,

    @Column(name = "max_consecutive_losses")
    val maxConsecutiveLosses: Int? = null,

    @Column(name = "stop_loss_count")
    val stopLossCount: Int = 0,

    @Column(name = "take_profit_count")
    val takeProfitCount: Int = 0,

    @Column(name = "trailing_stop_count")
    val trailingStopCount: Int = 0,

    // SCRUM-330: Risk Settings (JSONB)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_settings", columnDefinition = "jsonb")
    val riskSettings: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "position_sizing", columnDefinition = "jsonb")
    val positionSizing: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trading_costs", columnDefinition = "jsonb")
    val tradingCosts: String? = null,

    // Status
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var status: BacktestStatus = BacktestStatus.COMPLETED,

    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null,

    @OneToMany(mappedBy = "backtestResult", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val trades: MutableList<BacktestTradeEntity> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null
)

enum class BacktestStatus {
    RUNNING,
    COMPLETED,
    FAILED
}
