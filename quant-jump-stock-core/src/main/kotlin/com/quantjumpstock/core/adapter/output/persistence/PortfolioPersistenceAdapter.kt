package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.*
import com.quantjumpstock.core.domain.model.portfolio.PortfolioStock
import com.quantjumpstock.core.domain.model.portfolio.StrategyDefaultStock
import com.quantjumpstock.core.domain.model.portfolio.UserPortfolio
import com.quantjumpstock.core.domain.port.output.PortfolioStockRepository
import com.quantjumpstock.core.domain.port.output.StrategyDefaultStockRepository
import com.quantjumpstock.core.domain.port.output.UserPortfolioRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class StrategyDefaultStockPersistenceAdapter(
    private val jpaRepository: StrategyDefaultStockJpaRepository
) : StrategyDefaultStockRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun save(defaultStock: StrategyDefaultStock): StrategyDefaultStock {
        logger.debug("기본 종목 저장: strategyId=${defaultStock.strategyId}, stockId=${defaultStock.stockId}")
        val entity = toEntity(defaultStock)
        val saved = jpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): StrategyDefaultStock? {
        return jpaRepository.findById(id).orElse(null)?.let { toDomain(it) }
    }

    override fun findByStrategyId(strategyId: Long): List<StrategyDefaultStock> {
        return jpaRepository.findByStrategyId(strategyId).map { toDomain(it) }
    }

    override fun findByStrategyIdAndStockId(strategyId: Long, stockId: Long): StrategyDefaultStock? {
        return jpaRepository.findByStrategyIdAndStockId(strategyId, stockId)?.let { toDomain(it) }
    }

    override fun deleteById(id: Long) {
        jpaRepository.deleteById(id)
    }

    override fun deleteByStrategyId(strategyId: Long) {
        jpaRepository.deleteByStrategyId(strategyId)
    }

    private fun toDomain(entity: StrategyDefaultStockEntity): StrategyDefaultStock {
        return StrategyDefaultStock(
            id = entity.id,
            strategyId = entity.strategyId,
            stockId = entity.stockId,
            targetWeight = entity.targetWeight,
            memo = entity.memo,
            createdAt = entity.createdAt
        )
    }

    private fun toEntity(domain: StrategyDefaultStock): StrategyDefaultStockEntity {
        return StrategyDefaultStockEntity(
            id = domain.id,
            strategyId = domain.strategyId,
            stockId = domain.stockId,
            targetWeight = domain.targetWeight,
            memo = domain.memo,
            createdAt = domain.createdAt
        )
    }
}

@Component
class UserPortfolioPersistenceAdapter(
    private val jpaRepository: UserPortfolioJpaRepository
) : UserPortfolioRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun save(portfolio: UserPortfolio): UserPortfolio {
        logger.debug("포트폴리오 저장: userId=${portfolio.userId}, name=${portfolio.name}")
        val entity = toEntity(portfolio)
        val saved = jpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): UserPortfolio? {
        return jpaRepository.findById(id).orElse(null)?.let { toDomain(it) }
    }

    override fun findByUserId(userId: Long): List<UserPortfolio> {
        return jpaRepository.findByUserId(userId).map { toDomain(it) }
    }

    override fun findByUserIdAndStrategyId(userId: Long, strategyId: Long): UserPortfolio? {
        return jpaRepository.findByUserIdAndStrategyId(userId, strategyId)?.let { toDomain(it) }
    }

    override fun deleteById(id: Long) {
        jpaRepository.deleteById(id)
    }

    override fun existsById(id: Long): Boolean {
        return jpaRepository.existsById(id)
    }

    private fun toDomain(entity: UserPortfolioEntity): UserPortfolio {
        return UserPortfolio(
            id = entity.id,
            userId = entity.userId,
            strategyId = entity.strategyId,
            name = entity.name,
            description = entity.description,
            isDefault = entity.isDefault,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun toEntity(domain: UserPortfolio): UserPortfolioEntity {
        return UserPortfolioEntity(
            id = domain.id,
            userId = domain.userId,
            strategyId = domain.strategyId,
            name = domain.name,
            description = domain.description,
            isDefault = domain.isDefault,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}

@Component
class PortfolioStockPersistenceAdapter(
    private val jpaRepository: PortfolioStockJpaRepository
) : PortfolioStockRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun save(portfolioStock: PortfolioStock): PortfolioStock {
        logger.debug("포트폴리오 종목 저장: portfolioId=${portfolioStock.portfolioId}, stockId=${portfolioStock.stockId}")
        val entity = toEntity(portfolioStock)
        val saved = jpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): PortfolioStock? {
        return jpaRepository.findById(id).orElse(null)?.let { toDomain(it) }
    }

    override fun findByPortfolioId(portfolioId: Long): List<PortfolioStock> {
        return jpaRepository.findByPortfolioId(portfolioId).map { toDomain(it) }
    }

    override fun findByPortfolioIdAndStockId(portfolioId: Long, stockId: Long): PortfolioStock? {
        return jpaRepository.findByPortfolioIdAndStockId(portfolioId, stockId)?.let { toDomain(it) }
    }

    override fun deleteById(id: Long) {
        jpaRepository.deleteById(id)
    }

    override fun deleteByPortfolioId(portfolioId: Long) {
        jpaRepository.deleteByPortfolioId(portfolioId)
    }

    private fun toDomain(entity: PortfolioStockEntity): PortfolioStock {
        return PortfolioStock(
            id = entity.id,
            portfolioId = entity.portfolioId,
            stockId = entity.stockId,
            targetWeight = entity.targetWeight,
            isFromStrategy = entity.isFromStrategy,
            memo = entity.memo,
            addedAt = entity.addedAt
        )
    }

    private fun toEntity(domain: PortfolioStock): PortfolioStockEntity {
        return PortfolioStockEntity(
            id = domain.id,
            portfolioId = domain.portfolioId,
            stockId = domain.stockId,
            targetWeight = domain.targetWeight,
            isFromStrategy = domain.isFromStrategy,
            memo = domain.memo,
            addedAt = domain.addedAt
        )
    }
}
