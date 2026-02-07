package com.quantjumpstock.core.domain.model.marketplace

import com.quantjumpstock.core.domain.model.backtest.BacktestSummary
import com.quantjumpstock.core.domain.model.strategy.RebalanceFrequency
import com.quantjumpstock.core.domain.model.strategy.StockSelectionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 마켓플레이스 전략 상세 정보 도메인 모델
 * 전략 기본 정보, 성과 지표, 수익 곡선, 현재 보유 종목을 포함
 */
data class StrategyDetail(
    val id: Long,
    val name: String,
    val description: String?,
    val categoryId: Long,
    val categoryCode: String,
    val categoryName: String,
    val isPremium: Boolean,
    val stockSelectionType: StockSelectionType,
    val subscriberCount: Int,
    val averageRating: BigDecimal,
    val rebalanceFrequency: RebalanceFrequency,
    val latestBacktest: BacktestDetailSummary?,
    val currentHoldings: List<CurrentHolding>,
    val conditions: String = "{}",
    val createdAt: LocalDateTime
)

/**
 * 백테스트 상세 결과 (수익 곡선 포함)
 */
data class BacktestDetailSummary(
    val id: Long,
    val cagr: BigDecimal,
    val mdd: BigDecimal,
    val sharpeRatio: BigDecimal?,
    val sortinoRatio: BigDecimal?,
    val totalReturn: BigDecimal,
    val volatility: BigDecimal?,
    val winRate: BigDecimal?,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val avgWin: BigDecimal?,
    val avgLoss: BigDecimal?,
    val benchmarkReturn: BigDecimal?,
    val alpha: BigDecimal?,
    val beta: BigDecimal?,
    val initialCapital: BigDecimal,
    val finalValue: BigDecimal,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val equityCurve: List<EquityCurvePoint>,
    val trades: List<BacktestTradeDetail> = emptyList()
)

/**
 * 수익 곡선 포인트
 */
data class EquityCurvePoint(
    val date: LocalDate,
    val value: BigDecimal
)

/**
 * 거래 내역 상세
 */
data class BacktestTradeDetail(
    val tradeDate: LocalDate,
    val ticker: String,
    val side: String,
    val quantity: Int,
    val price: BigDecimal,
    val amount: BigDecimal,
    val pnl: BigDecimal?,
    val pnlPercent: BigDecimal?,
    val holdingDays: Int?,
    val signalReason: String?
)

/**
 * 현재 보유 종목 정보
 */
data class CurrentHolding(
    val ticker: String,
    val targetWeight: BigDecimal?,
    val signalDate: LocalDate,
    val reason: String?
)
