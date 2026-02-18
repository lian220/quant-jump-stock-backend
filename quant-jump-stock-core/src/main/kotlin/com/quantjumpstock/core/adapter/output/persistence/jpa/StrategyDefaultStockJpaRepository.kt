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

    // SCRUM-349: 전략 기본 종목 티커 목록 조회
    @Query("SELECT s.ticker FROM StrategyDefaultStockEntity e JOIN StockEntity s ON s.id = e.stockId WHERE e.strategyId = :strategyId AND s.isActive = true")
    fun findTickersByStrategyId(strategyId: Long): List<String>
}
