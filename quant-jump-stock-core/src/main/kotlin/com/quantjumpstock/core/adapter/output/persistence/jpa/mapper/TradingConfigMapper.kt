package com.quantjumpstock.core.adapter.output.persistence.jpa.mapper

import com.quantjumpstock.core.adapter.output.persistence.jpa.TradingConfigEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity
import com.quantjumpstock.core.domain.model.trading.TradingConfig
import org.springframework.stereotype.Component

/**
 * TradingConfig Mapper - JPA Entity ↔ Domain Model 변환
 */
@Component
class TradingConfigMapper {

    /**
     * JPA Entity → Domain Model
     */
    fun toDomain(entity: TradingConfigEntity): TradingConfig {
        return TradingConfig(
            id = entity.id,
            userId = entity.user.id ?: throw IllegalStateException("User ID is null"),
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

    /**
     * Domain Model → JPA Entity
     */
    fun toEntity(domain: TradingConfig, user: UserEntity): TradingConfigEntity {
        return TradingConfigEntity(
            id = domain.id,
            user = user,
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
