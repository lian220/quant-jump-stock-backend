package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface StrategyDefaultStockJpaRepository : JpaRepository<StrategyDefaultStockEntity, Long> {

    fun findByStrategyId(strategyId: Long): List<StrategyDefaultStockEntity>

    fun findByStrategyIdAndStockId(strategyId: Long, stockId: Long): StrategyDefaultStockEntity?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM StrategyDefaultStockEntity e WHERE e.strategyId = :strategyId")
    fun deleteByStrategyId(strategyId: Long)
}
