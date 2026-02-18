package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.portfolio.StrategyDefaultStock

interface StrategyDefaultStockRepository {

    fun save(defaultStock: StrategyDefaultStock): StrategyDefaultStock

    fun findById(id: Long): StrategyDefaultStock?

    fun findByStrategyId(strategyId: Long): List<StrategyDefaultStock>

    fun findByStrategyIdAndStockId(strategyId: Long, stockId: Long): StrategyDefaultStock?

    fun deleteById(id: Long)

    fun deleteByStrategyId(strategyId: Long)

    /** SCRUM-349: 전략 기본 종목 티커 목록 조회 (활성 종목만) */
    fun findTickersByStrategyId(strategyId: Long): List<String>
}
