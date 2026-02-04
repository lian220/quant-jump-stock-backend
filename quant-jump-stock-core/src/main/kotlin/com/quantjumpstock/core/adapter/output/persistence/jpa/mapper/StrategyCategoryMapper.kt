package com.quantjumpstock.core.adapter.output.persistence.jpa.mapper

import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyCategoryEntity
import com.quantjumpstock.core.domain.model.strategy.StrategyCategory
import org.springframework.stereotype.Component

/**
 * StrategyCategory Mapper - JPA Entity ↔ Domain Model 변환
 */
@Component
class StrategyCategoryMapper {

    /**
     * JPA Entity → Domain Model
     */
    fun toDomain(entity: StrategyCategoryEntity): StrategyCategory {
        return StrategyCategory(
            id = entity.id,
            code = entity.code,
            name = entity.name,
            description = entity.description,
            icon = entity.icon,
            sortOrder = entity.sortOrder,
            isActive = entity.isActive,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    /**
     * Domain Model → JPA Entity
     */
    fun toEntity(domain: StrategyCategory): StrategyCategoryEntity {
        return StrategyCategoryEntity(
            id = domain.id,
            code = domain.code,
            name = domain.name,
            description = domain.description,
            icon = domain.icon,
            sortOrder = domain.sortOrder,
            isActive = domain.isActive,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
