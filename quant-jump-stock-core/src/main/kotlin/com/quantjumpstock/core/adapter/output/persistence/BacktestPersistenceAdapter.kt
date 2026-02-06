package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestResultEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestResultJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestTradeEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestTradeJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestStatus as JpaBacktestStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestTradeSide as JpaBacktestTradeSide
import com.quantjumpstock.core.domain.model.backtest.BacktestResult
import com.quantjumpstock.core.domain.model.backtest.BacktestStatus
import com.quantjumpstock.core.domain.model.backtest.BacktestTrade
import com.quantjumpstock.core.domain.model.backtest.BacktestTradeSide
import com.quantjumpstock.core.domain.port.output.BacktestResultRepository
import com.quantjumpstock.core.domain.port.output.BacktestTradeRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

/**
 * Backtest Persistence Adapter (Output Adapter)
 *
 * BacktestResultRepository, BacktestTradeRepository 인터페이스를 구현하여 JPA와 연동합니다.
 *
 * 특징:
 * - 도메인 모델(BacktestResult/BacktestTrade)과 JPA Entity 간 변환
 * - JPA enum과 Domain enum 간 변환
 * - StrategyEntity, UserEntity 참조를 ID 기반으로 조회하여 연결
 */
@Component
class BacktestPersistenceAdapter(
    private val backtestResultJpaRepository: BacktestResultJpaRepository,
    private val backtestTradeJpaRepository: BacktestTradeJpaRepository,
    private val strategyJpaRepository: StrategyJpaRepository,
    private val userJpaRepository: UserJpaRepository
) : BacktestResultRepository, BacktestTradeRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    // ===== BacktestResultRepository =====

    override fun save(result: BacktestResult): BacktestResult {
        logger.debug("백테스트 결과 저장: strategyId={}", result.strategyId)
        val entity = toEntity(result)
        val saved = backtestResultJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): BacktestResult? {
        logger.debug("백테스트 결과 조회: id={}", id)
        return backtestResultJpaRepository.findById(id).orElse(null)?.let { toDomain(it) }
    }

    override fun findByRequestId(requestId: String): BacktestResult? {
        logger.debug("백테스트 결과 조회: requestId={}", requestId)
        return backtestResultJpaRepository.findByRequestId(requestId)?.let { toDomain(it) }
    }

    override fun findByIdWithTrades(id: Long): BacktestResult? {
        logger.debug("백테스트 결과 조회 (거래 포함): id={}", id)
        return backtestResultJpaRepository.findByIdWithTrades(id).orElse(null)?.let { toDomainWithTrades(it) }
    }

    override fun findByStrategyIdOrderByCreatedAtDesc(strategyId: Long, pageable: Pageable): Page<BacktestResult> {
        logger.debug("전략별 백테스트 결과 조회: strategyId={}", strategyId)
        return backtestResultJpaRepository.findByStrategyIdOrderByCreatedAtDesc(strategyId, pageable)
            .map { toDomain(it) }
    }

    override fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<BacktestResult> {
        logger.debug("사용자별 백테스트 결과 조회: userId={}", userId)
        return backtestResultJpaRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map { toDomain(it) }
    }

    override fun findAll(pageable: Pageable): Page<BacktestResult> {
        logger.debug("전체 백테스트 결과 조회")
        return backtestResultJpaRepository.findAll(pageable).map { toDomain(it) }
    }

    // ===== BacktestTradeRepository =====

    override fun saveAll(trades: List<BacktestTrade>): List<BacktestTrade> {
        if (trades.isEmpty()) return emptyList()

        logger.debug("백테스트 거래 내역 일괄 저장: count={}", trades.size)

        // Pre-fetch: 동일 BacktestResult를 N번 조회하지 않도록 미리 로드
        val resultIds = trades.mapNotNull { it.backtestResultId }.distinct()
        val resultEntityMap = resultIds.associateWith { id ->
            backtestResultJpaRepository.findById(id).orElseThrow {
                IllegalArgumentException("BacktestResult not found: $id")
            }
        }

        val entities = trades.map { trade ->
            val backtestResultId = trade.backtestResultId
                ?: throw IllegalArgumentException("backtestResultId is required for saving trades")
            val backtestResultEntity = resultEntityMap[backtestResultId]
                ?: throw IllegalArgumentException("BacktestResult not found: $backtestResultId")
            toEntity(trade, backtestResultEntity)
        }
        val saved = backtestTradeJpaRepository.saveAll(entities)
        return saved.map { toDomain(it) }
    }

    // ===== Mapping: BacktestResult =====

    private fun toDomain(entity: BacktestResultEntity): BacktestResult {
        return BacktestResult(
            id = entity.id,
            requestId = entity.requestId,
            strategyId = entity.strategy.id!!,
            strategyName = entity.strategy.name,
            userId = entity.user?.id,
            startDate = entity.startDate,
            endDate = entity.endDate,
            initialCapital = entity.initialCapital,
            benchmark = entity.benchmark,
            finalValue = entity.finalValue,
            totalReturn = entity.totalReturn,
            cagr = entity.cagr,
            mdd = entity.mdd,
            sharpeRatio = entity.sharpeRatio,
            sortinoRatio = entity.sortinoRatio,
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
            equityCurve = entity.equityCurve,
            status = mapStatus(entity.status),
            errorMessage = entity.errorMessage,
            trades = emptyList(),
            createdAt = entity.createdAt,
            completedAt = entity.completedAt
        )
    }

    private fun toDomainWithTrades(entity: BacktestResultEntity): BacktestResult {
        return BacktestResult(
            id = entity.id,
            requestId = entity.requestId,
            strategyId = entity.strategy.id!!,
            strategyName = entity.strategy.name,
            userId = entity.user?.id,
            startDate = entity.startDate,
            endDate = entity.endDate,
            initialCapital = entity.initialCapital,
            benchmark = entity.benchmark,
            finalValue = entity.finalValue,
            totalReturn = entity.totalReturn,
            cagr = entity.cagr,
            mdd = entity.mdd,
            sharpeRatio = entity.sharpeRatio,
            sortinoRatio = entity.sortinoRatio,
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
            equityCurve = entity.equityCurve,
            status = mapStatus(entity.status),
            errorMessage = entity.errorMessage,
            trades = entity.trades.map { toDomain(it) },
            createdAt = entity.createdAt,
            completedAt = entity.completedAt
        )
    }

    private fun toEntity(domain: BacktestResult): BacktestResultEntity {
        val strategyEntity = strategyJpaRepository.findById(domain.strategyId)
            .orElseThrow { IllegalArgumentException("Strategy not found: ${domain.strategyId}") }

        val userEntity = domain.userId?.let { userId ->
            userJpaRepository.findById(userId).orElse(null)
        }

        return BacktestResultEntity(
            id = domain.id,
            requestId = domain.requestId,
            strategy = strategyEntity,
            user = userEntity,
            startDate = domain.startDate,
            endDate = domain.endDate,
            initialCapital = domain.initialCapital,
            benchmark = domain.benchmark,
            finalValue = domain.finalValue,
            totalReturn = domain.totalReturn,
            cagr = domain.cagr,
            mdd = domain.mdd,
            sharpeRatio = domain.sharpeRatio,
            sortinoRatio = domain.sortinoRatio,
            volatility = domain.volatility,
            winRate = domain.winRate,
            totalTrades = domain.totalTrades,
            winningTrades = domain.winningTrades,
            losingTrades = domain.losingTrades,
            avgWin = domain.avgWin,
            avgLoss = domain.avgLoss,
            benchmarkReturn = domain.benchmarkReturn,
            alpha = domain.alpha,
            beta = domain.beta,
            equityCurve = domain.equityCurve,
            status = mapStatus(domain.status),
            errorMessage = domain.errorMessage,
            createdAt = domain.createdAt,
            completedAt = domain.completedAt
        )
    }

    // ===== Mapping: BacktestTrade =====

    private fun toDomain(entity: BacktestTradeEntity): BacktestTrade {
        return BacktestTrade(
            id = entity.id,
            backtestResultId = entity.backtestResult.id,
            tradeDate = entity.tradeDate,
            ticker = entity.ticker,
            side = mapTradeSide(entity.side),
            quantity = entity.quantity,
            price = entity.price,
            amount = entity.amount,
            commission = entity.commission,
            pnl = entity.pnl,
            pnlPercent = entity.pnlPercent,
            holdingDays = entity.holdingDays,
            signalReason = entity.signalReason,
            createdAt = entity.createdAt
        )
    }

    private fun toEntity(domain: BacktestTrade, backtestResultEntity: BacktestResultEntity): BacktestTradeEntity {
        return BacktestTradeEntity(
            id = domain.id,
            backtestResult = backtestResultEntity,
            tradeDate = domain.tradeDate,
            ticker = domain.ticker,
            side = mapTradeSide(domain.side),
            quantity = domain.quantity,
            price = domain.price,
            amount = domain.amount,
            commission = domain.commission,
            pnl = domain.pnl,
            pnlPercent = domain.pnlPercent,
            holdingDays = domain.holdingDays,
            signalReason = domain.signalReason,
            createdAt = domain.createdAt
        )
    }

    // ===== Enum Mapping =====

    private fun mapStatus(jpaStatus: JpaBacktestStatus): BacktestStatus = when (jpaStatus) {
        JpaBacktestStatus.RUNNING -> BacktestStatus.RUNNING
        JpaBacktestStatus.COMPLETED -> BacktestStatus.COMPLETED
        JpaBacktestStatus.FAILED -> BacktestStatus.FAILED
    }

    private fun mapStatus(domainStatus: BacktestStatus): JpaBacktestStatus = when (domainStatus) {
        BacktestStatus.RUNNING -> JpaBacktestStatus.RUNNING
        BacktestStatus.COMPLETED -> JpaBacktestStatus.COMPLETED
        BacktestStatus.FAILED -> JpaBacktestStatus.FAILED
    }

    private fun mapTradeSide(jpaSide: JpaBacktestTradeSide): BacktestTradeSide = when (jpaSide) {
        JpaBacktestTradeSide.BUY -> BacktestTradeSide.BUY
        JpaBacktestTradeSide.SELL -> BacktestTradeSide.SELL
    }

    private fun mapTradeSide(domainSide: BacktestTradeSide): JpaBacktestTradeSide = when (domainSide) {
        BacktestTradeSide.BUY -> JpaBacktestTradeSide.BUY
        BacktestTradeSide.SELL -> JpaBacktestTradeSide.SELL
    }
}
