package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.TradingConfigEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.TradingConfigJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.domain.model.trading.TradingConfig
import com.quantjumpstock.core.domain.port.output.TradingConfigRepository
import org.springframework.stereotype.Component

/**
 * TradingConfig Persistence Adapter (Output Adapter)
 * TradingConfigRepository 인터페이스를 구현하여 JPA와 연동합니다.
 */
@Component
class TradingConfigPersistenceAdapter(
    private val tradingConfigJpaRepository: TradingConfigJpaRepository,
    private val userJpaRepository: UserJpaRepository
) : TradingConfigRepository {

    override fun save(config: TradingConfig): TradingConfig {
        val userEntity = userJpaRepository.findById(config.userId).orElseThrow {
            IllegalArgumentException("User not found: ${config.userId}")
        }
        val entity = toEntity(config, userEntity)
        val saved = tradingConfigJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): TradingConfig? {
        return tradingConfigJpaRepository.findById(id).orElse(null)?.let { toDomain(it) }
    }

    override fun findByUserId(userId: Long): TradingConfig? {
        return tradingConfigJpaRepository.findByUserId(userId).orElse(null)?.let { toDomain(it) }
    }

    override fun findAllEnabledWithAutoTrading(): List<TradingConfig> {
        return tradingConfigJpaRepository.findAllEnabledWithAutoTrading().map { toDomain(it) }
    }

    override fun findAllEnabled(): List<TradingConfig> {
        return tradingConfigJpaRepository.findAllEnabled().map { toDomain(it) }
    }

    override fun deleteById(id: Long) {
        tradingConfigJpaRepository.deleteById(id)
    }

    override fun existsByUserId(userId: Long): Boolean {
        return tradingConfigJpaRepository.existsByUserId(userId)
    }

    override fun countAutoTradingEnabled(): Long {
        return tradingConfigJpaRepository.countAutoTradingEnabled()
    }

    // ===== Mapping Functions =====

    private fun toDomain(entity: TradingConfigEntity): TradingConfig {
        return TradingConfig(
            id = entity.id,
            userId = entity.user.id!!,
            enabled = entity.enabled,
            autoTradingEnabled = entity.autoTradingEnabled,
            minCompositeScore = entity.minCompositeScore,
            maxStocksToBuy = entity.maxStocksToBuy,
            maxAmountPerStock = entity.maxAmountPerStock,
            stopLossPercent = entity.stopLossPercent,
            takeProfitPercent = entity.takeProfitPercent,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun toEntity(domain: TradingConfig, userEntity: com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity): TradingConfigEntity {
        return TradingConfigEntity(
            id = domain.id,
            user = userEntity,
            enabled = domain.enabled,
            autoTradingEnabled = domain.autoTradingEnabled,
            minCompositeScore = domain.minCompositeScore,
            maxStocksToBuy = domain.maxStocksToBuy,
            maxAmountPerStock = domain.maxAmountPerStock,
            stopLossPercent = domain.stopLossPercent,
            takeProfitPercent = domain.takeProfitPercent,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
