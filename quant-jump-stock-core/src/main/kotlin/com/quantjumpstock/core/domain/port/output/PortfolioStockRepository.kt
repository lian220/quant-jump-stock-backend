package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.portfolio.PortfolioStock

interface PortfolioStockRepository {

    fun save(portfolioStock: PortfolioStock): PortfolioStock

    fun findById(id: Long): PortfolioStock?

    fun findByPortfolioId(portfolioId: Long): List<PortfolioStock>

    fun findByPortfolioIdAndStockId(portfolioId: Long, stockId: Long): PortfolioStock?

    fun countByPortfolioIdIn(portfolioIds: List<Long>): Map<Long, Int>

    fun deleteById(id: Long)

    fun deleteByPortfolioId(portfolioId: Long)
}
