package com.quantjumpstock.core.events

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Base Event Schema
 * 모든 이벤트의 공통 구조를 정의합니다.
 */
data class BaseEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: String,
    val version: String = "1.0",
    val timestamp: String = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toString(),
    val source: String = "quantiq-core",
    val payload: Any
)

// ============================================================================
// Stock Events
// ============================================================================

data class StockPriceUpdatedPayload(
    val symbol: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val volume: Long,
    val marketCap: Long?
)

data class StockDataSyncRequestedPayload(
    val requestId: String,
    val symbols: List<String>,
    val syncType: String, // full, incremental
    val priority: String = "normal" // high, normal, low
)

data class StockDataRefreshedPayload(
    val requestId: String,
    val symbols: List<String>,
    val recordsUpdated: Int,
    val duration: Double,
    val status: String
)

// ============================================================================
// Trading Events
// ============================================================================

data class TradingOrderCreatedPayload(
    val orderId: String,
    val userId: String,
    val symbol: String,
    val orderType: String, // market, limit
    val side: String, // buy, sell
    val quantity: Int,
    val price: Double?,
    val status: String
)

data class TradingOrderExecutedPayload(
    val orderId: String,
    val executedPrice: Double,
    val executedQuantity: Int,
    val commission: Double,
    val totalAmount: Double
)

data class TradingSignalDetectedPayload(
    val symbol: String,
    val signalType: String, // buy, sell
    val confidence: Double,
    val indicators: Map<String, Any>,
    val recommendedAction: String,
    val recommendedQuantity: Int
)

data class TradingBalanceUpdatedPayload(
    val userId: String,
    val currency: String,
    val balance: Double,
    val availableBalance: Double,
    val holdBalance: Double
)

// ============================================================================
// Analysis Events
// ============================================================================

data class AnalysisRequestPayload(
    val requestId: String,
    val analysisType: String, // technical, fundamental, sentiment
    val symbols: List<String>,
    val parameters: Map<String, Any> = emptyMap()
)

data class AnalysisCompletedPayload(
    val requestId: String,
    val analysisType: String,
    val symbols: List<String>,
    val recordsProcessed: Int,
    val duration: Double,
    val status: String
)

data class AnalysisRecommendationGeneratedPayload(
    val symbol: String,
    val recommendation: String, // buy, hold, sell
    val targetPrice: Double,
    val stopLoss: Double,
    val confidence: Double,
    val timeframe: String, // short, medium, long
    val reasoning: String
)

data class AnalysisPredictionCompletedPayload(
    val symbol: String,
    val predictedPrice: Double,
    val confidence: Double,
    val timeframe: String,
    val model: String
)

// ============================================================================
// Backtest Events
// ============================================================================

data class BacktestRequestPayload(
    val requestId: String,
    val strategyId: Long,
    val startDate: String,      // yyyy-MM-dd
    val endDate: String,        // yyyy-MM-dd
    val initialCapital: String, // BigDecimal as String for JSON serialization
    val userId: String?
)

data class BacktestCompletedPayload(
    val requestId: String,
    val strategyId: Long,
    val status: String,         // success, failed
    val totalReturn: Double?,
    val sharpeRatio: Double?,
    val maxDrawdown: Double?,
    val winRate: Double?,
    val duration: Double,       // execution time in seconds
    val errorMessage: String? = null
)

// ============================================================================
// Economic Events
// ============================================================================

data class EconomicDataSyncRequestedPayload(
    val requestId: String,
    val dataTypes: List<String>,
    val source: String, // scheduled, manual
    val priority: String = "normal"
)

data class EconomicDataUpdatedPayload(
    val requestId: String,
    val dataTypes: List<String>,
    val recordsUpdated: Int,
    val duration: Double,
    val status: String
)

data class EconomicDataSyncFailedPayload(
    val requestId: String,
    val errorCode: String,
    val errorMessage: String,
    val retryable: Boolean,
    val retryAfter: Int?
)

// ============================================================================
// Event Topics (Constants)
// ============================================================================

object EventTopics {
    // Stock
    const val STOCK_PRICE_UPDATED = "quantiq.stock.price.updated"
    const val STOCK_DATA_SYNC_REQUESTED = "quantiq.stock.data.sync.requested"
    const val STOCK_DATA_REFRESHED = "quantiq.stock.data.refreshed"

    // Trading
    const val TRADING_ORDER_CREATED = "quantiq.trading.order.created"
    const val TRADING_ORDER_EXECUTED = "quantiq.trading.order.executed"
    const val TRADING_ORDER_CANCELLED = "quantiq.trading.order.cancelled"
    const val TRADING_SIGNAL_DETECTED = "quantiq.trading.signal.detected"
    const val TRADING_BALANCE_UPDATED = "quantiq.trading.balance.updated"

    // Analysis
    const val ANALYSIS_REQUEST = "quantiq.analysis.request"
    const val ANALYSIS_COMPLETED = "quantiq.analysis.completed"
    const val ANALYSIS_RECOMMENDATION_GENERATED = "quantiq.analysis.recommendation.generated"
    const val ANALYSIS_PREDICTION_COMPLETED = "quantiq.analysis.prediction.completed"

    // Economic
    const val ECONOMIC_DATA_SYNC_REQUESTED = "quantiq.economic.data.sync.requested"
    const val ECONOMIC_DATA_UPDATED = "quantiq.economic.data.updated"
    const val ECONOMIC_DATA_SYNC_FAILED = "quantiq.economic.data.sync.failed"

    // Backtest
    const val BACKTEST_REQUEST = "quantiq.backtest.request"
    const val BACKTEST_COMPLETED = "quantiq.backtest.completed"
    const val BACKTEST_FAILED = "quantiq.backtest.failed"

    // Legacy (backward compatibility)
    @Deprecated("Use ANALYSIS_REQUEST instead")
    const val LEGACY_ANALYSIS_REQUEST = "quantiq.analysis.request"

    @Deprecated("Use ANALYSIS_COMPLETED instead")
    const val LEGACY_ANALYSIS_COMPLETED = "quantiq.analysis.completed"

    @Deprecated("Use ECONOMIC_DATA_SYNC_REQUESTED instead")
    const val LEGACY_ECONOMIC_DATA_UPDATE_REQUEST = "economic.data.update.request"
}
