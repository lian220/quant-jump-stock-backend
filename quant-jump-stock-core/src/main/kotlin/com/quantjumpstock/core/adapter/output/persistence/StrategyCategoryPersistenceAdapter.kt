package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyCategoryEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyCategoryJpaRepository
import com.quantjumpstock.core.domain.model.strategy.StrategyCategory
import com.quantjumpstock.core.domain.port.output.StrategyCategoryRepository
import org.springframework.stereotype.Component

/**
 * StrategyCategory Persistence Adapter (Output Adapter)
 * StrategyCategoryRepository 인터페이스를 구현하여 JPA와 연동합니다.
 */
@Component
class StrategyCategoryPersistenceAdapter(
    private val categoryJpaRepository: StrategyCategoryJpaRepository
) : StrategyCategoryRepository {

    override fun save(category: StrategyCategory): StrategyCategory {
        val entity = toEntity(category)
        val saved = categoryJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): StrategyCategory? {
        return categoryJpaRepository.findById(id).orElse(null)?.let { toDomain(it) }
    }

    override fun findByCode(code: String): StrategyCategory? {
        return categoryJpaRepository.findByCode(code)?.let { toDomain(it) }
    }

    override fun findAll(): List<StrategyCategory> {
        return categoryJpaRepository.findAll().map { toDomain(it) }
    }

    override fun findAllActive(): List<StrategyCategory> {
        return categoryJpaRepository.findByIsActiveTrueOrderBySortOrderAsc().map { toDomain(it) }
    }

    override fun deleteById(id: Long) {
        categoryJpaRepository.deleteById(id)
    }

    override fun existsById(id: Long): Boolean {
        return categoryJpaRepository.existsById(id)
    }

    override fun existsByCode(code: String): Boolean {
        return categoryJpaRepository.existsByCode(code)
    }

    // ===== Mapping Functions =====

    private fun toDomain(entity: StrategyCategoryEntity): StrategyCategory {
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

    private fun toEntity(domain: StrategyCategory): StrategyCategoryEntity {
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
