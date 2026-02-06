package com.quantjumpstock.core.domain.model.backtest

import com.quantjumpstock.core.domain.port.output.Benchmark
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 백테스트 결과 도메인 모델
 *
 * JPA Entity가 아닌 순수 도메인 객체입니다.
 * 영속성 관련 어노테이션 없이 비즈니스 로직만 포함합니다.
 */
data class BacktestResult(
    val id: Long? = null,
    val requestId: String? = null,
    val strategyId: Long,
    val strategyName: String? = null,
    val userId: Long? = null,

    // Test Settings
    val startDate: LocalDate,
    val endDate: LocalDate,
    val initialCapital: BigDecimal,
    val benchmark: String = Benchmark.DEFAULT_TICKER,

    // Performance Metrics
    val finalValue: BigDecimal,
    val totalReturn: BigDecimal,
    val cagr: BigDecimal,
    val mdd: BigDecimal,
    val sharpeRatio: BigDecimal? = null,
    val sortinoRatio: BigDecimal? = null,
    val volatility: BigDecimal? = null,
    val winRate: BigDecimal? = null,

    // Trade Statistics
    val totalTrades: Int = 0,
    val winningTrades: Int = 0,
    val losingTrades: Int = 0,
    val avgWin: BigDecimal? = null,
    val avgLoss: BigDecimal? = null,

    // Benchmark Comparison
    val benchmarkReturn: BigDecimal? = null,
    val alpha: BigDecimal? = null,
    val beta: BigDecimal? = null,

    // Equity Curve (JSON)
    val equityCurve: String? = null,

    // Status
    val status: BacktestStatus = BacktestStatus.COMPLETED,
    val errorMessage: String? = null,

    // Trades
    val trades: List<BacktestTrade> = emptyList(),

    // Timestamps
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val completedAt: LocalDateTime? = null
)
