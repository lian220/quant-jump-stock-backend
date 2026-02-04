package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestResultEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestStatus as JpaBacktestStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.RebalanceFrequency as JpaRebalanceFrequency
import com.quantjumpstock.core.domain.model.backtest.BacktestStatus
import com.quantjumpstock.core.domain.model.backtest.BacktestSummary
import com.quantjumpstock.core.domain.model.marketplace.MarketplaceStrategy
import com.quantjumpstock.core.domain.model.strategy.RebalanceFrequency
import com.quantjumpstock.core.domain.port.output.MarketplaceRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Marketplace Persistence Adapter (Output Adapter)
 * MarketplaceRepository 인터페이스를 구현하여 JPA와 연동합니다.
 */
@Component
class MarketplacePersistenceAdapter(
    private val strategyJpaRepository: StrategyJpaRepository
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

    // ===== Mapping Functions =====

    private fun toMarketplaceStrategy(entity: StrategyEntity): MarketplaceStrategy {
        val latestBacktest = entity.backtestResults
            .filter { it.status == JpaBacktestStatus.COMPLETED }
            .maxByOrNull { it.createdAt }

        return MarketplaceStrategy(
            id = entity.id!!,
            name = entity.name,
            description = entity.description,
            categoryId = entity.category.id!!,
            categoryCode = entity.category.code,
            categoryName = entity.category.name,
            isPremium = entity.isPremium,
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

    private fun mapBacktestStatus(jpaStatus: JpaBacktestStatus): BacktestStatus = when (jpaStatus) {
        JpaBacktestStatus.RUNNING -> BacktestStatus.RUNNING
        JpaBacktestStatus.COMPLETED -> BacktestStatus.COMPLETED
        JpaBacktestStatus.FAILED -> BacktestStatus.FAILED
    }
}
