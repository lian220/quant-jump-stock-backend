package com.quantjumpstock.core.adapter.output.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestResultEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.SignalType
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategySignalEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategySignalJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestStatus as JpaBacktestStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.RebalanceFrequency as JpaRebalanceFrequency
import com.quantjumpstock.core.domain.model.backtest.BacktestStatus
import com.quantjumpstock.core.domain.model.backtest.BacktestSummary
import com.quantjumpstock.core.domain.model.marketplace.BacktestDetailSummary
import com.quantjumpstock.core.domain.model.marketplace.CurrentHolding
import com.quantjumpstock.core.domain.model.marketplace.EquityCurvePoint
import com.quantjumpstock.core.domain.model.marketplace.MarketplaceStrategy
import com.quantjumpstock.core.domain.model.marketplace.StrategyDetail
import com.quantjumpstock.core.domain.model.marketplace.BacktestTradeDetail
import com.quantjumpstock.core.domain.model.strategy.RebalanceFrequency
import com.quantjumpstock.core.domain.model.strategy.StockSelectionType
import com.quantjumpstock.core.adapter.output.persistence.jpa.StockSelectionType as JpaStockSelectionType
import com.quantjumpstock.core.domain.port.output.MarketplaceRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Marketplace Persistence Adapter (Output Adapter)
 * MarketplaceRepository 인터페이스를 구현하여 JPA와 연동합니다.
 */
@Component
class MarketplacePersistenceAdapter(
    private val strategyJpaRepository: StrategyJpaRepository,
    private val strategySignalJpaRepository: StrategySignalJpaRepository,
    private val objectMapper: ObjectMapper
) : MarketplaceRepository {

    override fun findMarketplaceStrategies(
        categoryCode: String?,
        minCagr: BigDecimal?,
        maxMdd: BigDecimal?,
        pageable: Pageable
    ): Page<MarketplaceStrategy> {
        return strategyJpaRepository.findMarketplaceStrategies(categoryCode, minCagr, maxMdd, pageable)
            .map { toMarketplaceStrategy(it) }
    }

    override fun findMarketplaceStrategiesByCagr(
        categoryCode: String?,
        minCagr: BigDecimal?,
        maxMdd: BigDecimal?,
        pageable: Pageable
    ): Page<MarketplaceStrategy> {
        return strategyJpaRepository.findMarketplaceStrategiesByCagr(categoryCode, minCagr, maxMdd, pageable)
            .map { toMarketplaceStrategy(it) }
    }

    override fun findMarketplaceStrategiesBySharpe(
        categoryCode: String?,
        minCagr: BigDecimal?,
        maxMdd: BigDecimal?,
        pageable: Pageable
    ): Page<MarketplaceStrategy> {
        return strategyJpaRepository.findMarketplaceStrategiesBySharpe(categoryCode, minCagr, maxMdd, pageable)
            .map { toMarketplaceStrategy(it) }
    }

    override fun findPublicStrategyById(id: Long): StrategyDetail? {
        val strategyEntity = strategyJpaRepository.findPublicByIdWithBacktestResults(id)
            .orElse(null) ?: return null

        // 현재 보유 종목 조회 (최근 30일 내 BUY 또는 REBALANCE 신호 기반)
        val since = LocalDate.now().minusDays(30)
        val recentSignals = strategySignalJpaRepository.findRecentSignals(id, since)
            .filter { it.signalType == SignalType.BUY || it.signalType == SignalType.REBALANCE }
            .filter { it.ticker != null }

        // 종목별 최신 신호만 추출
        val currentHoldings = recentSignals
            .groupBy { it.ticker!! }
            .map { (_, signals) -> signals.maxByOrNull { it.signalDate }!! }
            .map { toCurrentHolding(it) }
            .sortedByDescending { it.signalDate }

        return toStrategyDetail(strategyEntity, currentHoldings)
    }

    // ===== Mapping Functions =====

    private fun toMarketplaceStrategy(entity: StrategyEntity): MarketplaceStrategy {
        // equity_curve가 있는 최신 COMPLETED 백테스트 우선 선택
        val completedBacktests = entity.backtestResults
            .filter { it.status == JpaBacktestStatus.COMPLETED }
        val latestBacktest = completedBacktests
            .filter { !it.equityCurve.isNullOrBlank() }
            .maxByOrNull { it.createdAt }
            ?: completedBacktests.maxByOrNull { it.createdAt }

        return MarketplaceStrategy(
            id = entity.id!!,
            name = entity.name,
            description = entity.description,
            categoryId = entity.category.id!!,
            categoryCode = entity.category.code,
            categoryName = entity.category.name,
            isPremium = entity.isPremium,
            stockSelectionType = mapStockSelectionType(entity.stockSelectionType),
            subscriberCount = entity.subscriberCount,
            averageRating = entity.averageRating,
            rebalanceFrequency = mapRebalanceFrequency(entity.rebalanceFrequency),
            latestBacktest = latestBacktest?.let { toBacktestSummary(it) },
            createdAt = entity.createdAt
        )
    }

    private fun toBacktestSummary(entity: BacktestResultEntity): BacktestSummary {
        return BacktestSummary(
            id = entity.id!!,
            cagr = entity.cagr,
            mdd = entity.mdd,
            sharpeRatio = entity.sharpeRatio,
            totalReturn = entity.totalReturn,
            volatility = entity.volatility,
            winRate = entity.winRate,
            startDate = entity.startDate,
            endDate = entity.endDate,
            status = mapBacktestStatus(entity.status)
        )
    }

    private fun mapRebalanceFrequency(jpaFreq: JpaRebalanceFrequency): RebalanceFrequency = when (jpaFreq) {
        JpaRebalanceFrequency.DAILY -> RebalanceFrequency.DAILY
        JpaRebalanceFrequency.WEEKLY -> RebalanceFrequency.WEEKLY
        JpaRebalanceFrequency.MONTHLY -> RebalanceFrequency.MONTHLY
        JpaRebalanceFrequency.QUARTERLY -> RebalanceFrequency.QUARTERLY
        JpaRebalanceFrequency.YEARLY -> RebalanceFrequency.YEARLY
        JpaRebalanceFrequency.NONE -> RebalanceFrequency.NONE
    }

    private fun mapStockSelectionType(jpaType: JpaStockSelectionType): StockSelectionType = when (jpaType) {
        JpaStockSelectionType.SCREENING -> StockSelectionType.SCREENING
        JpaStockSelectionType.PORTFOLIO -> StockSelectionType.PORTFOLIO
    }

    private fun mapBacktestStatus(jpaStatus: JpaBacktestStatus): BacktestStatus = when (jpaStatus) {
        JpaBacktestStatus.RUNNING -> BacktestStatus.RUNNING
        JpaBacktestStatus.COMPLETED -> BacktestStatus.COMPLETED
        JpaBacktestStatus.FAILED -> BacktestStatus.FAILED
    }

    private fun toStrategyDetail(entity: StrategyEntity, currentHoldings: List<CurrentHolding>): StrategyDetail {
        // equity_curve가 있는 최신 COMPLETED 백테스트 우선 선택, 없으면 최신 COMPLETED
        val completedBacktests = entity.backtestResults
            .filter { it.status == JpaBacktestStatus.COMPLETED }
        val latestBacktest = completedBacktests
            .filter { !it.equityCurve.isNullOrBlank() }
            .maxByOrNull { it.createdAt }
            ?: completedBacktests.maxByOrNull { it.createdAt }

        return StrategyDetail(
            id = entity.id!!,
            name = entity.name,
            description = entity.description,
            categoryId = entity.category.id!!,
            categoryCode = entity.category.code,
            categoryName = entity.category.name,
            isPremium = entity.isPremium,
            stockSelectionType = mapStockSelectionType(entity.stockSelectionType),
            subscriberCount = entity.subscriberCount,
            averageRating = entity.averageRating,
            rebalanceFrequency = mapRebalanceFrequency(entity.rebalanceFrequency),
            latestBacktest = latestBacktest?.let { toBacktestDetailSummary(it) },
            currentHoldings = currentHoldings,
            conditions = entity.conditions,
            createdAt = entity.createdAt
        )
    }

    private fun toBacktestDetailSummary(entity: BacktestResultEntity): BacktestDetailSummary {
        val equityCurve = parseEquityCurve(entity.equityCurve)

        return BacktestDetailSummary(
            id = entity.id!!,
            cagr = entity.cagr,
            mdd = entity.mdd,
            sharpeRatio = entity.sharpeRatio,
            sortinoRatio = entity.sortinoRatio,
            totalReturn = entity.totalReturn,
            volatility = entity.volatility,
            winRate = entity.winRate,
            totalTrades = entity.totalTrades,
            winningTrades = entity.winningTrades,
            losingTrades = entity.losingTrades,
            avgWin = entity.avgWin,
            avgLoss = entity.avgLoss,
            benchmarkReturn = entity.benchmarkReturn,
            alpha = entity.alpha,
            beta = entity.beta,
            initialCapital = entity.initialCapital,
            finalValue = entity.finalValue,
            startDate = entity.startDate,
            endDate = entity.endDate,
            equityCurve = equityCurve,
            trades = entity.trades.map { trade ->
                BacktestTradeDetail(
                    tradeDate = trade.tradeDate,
                    ticker = trade.ticker,
                    side = trade.side.name,
                    quantity = trade.quantity,
                    price = trade.price,
                    amount = trade.amount,
                    pnl = trade.pnl,
                    pnlPercent = trade.pnlPercent,
                    holdingDays = trade.holdingDays,
                    signalReason = trade.signalReason
                )
            }.sortedBy { it.tradeDate }
        )
    }

    private fun toCurrentHolding(entity: StrategySignalEntity): CurrentHolding {
        return CurrentHolding(
            ticker = entity.ticker!!,
            targetWeight = entity.targetWeight,
            signalDate = entity.signalDate,
            reason = entity.reason
        )
    }

    private fun parseEquityCurve(equityCurveJson: String?): List<EquityCurvePoint> {
        if (equityCurveJson.isNullOrBlank()) return emptyList()

        return try {
            val rawData: List<Map<String, Any>> = objectMapper.readValue(equityCurveJson)
            rawData.mapNotNull { point ->
                val date = point["date"]?.toString()?.let { LocalDate.parse(it) }
                val value = (point["equity"] ?: point["value"])?.let { BigDecimal(it.toString()) }
                if (date != null && value != null) {
                    EquityCurvePoint(date = date, value = value)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
