package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.strategy.StrategyCategory

/**
 * StrategyCategory Repository Port - 도메인 포트
 */
interface StrategyCategoryRepository {

    fun save(category: StrategyCategory): StrategyCategory

    fun findById(id: Long): StrategyCategory?

    fun findByCode(code: String): StrategyCategory?

    fun findAll(): List<StrategyCategory>

    fun findAllActive(): List<StrategyCategory>

    fun deleteById(id: Long)

    fun existsById(id: Long): Boolean

    fun existsByCode(code: String): Boolean
}
