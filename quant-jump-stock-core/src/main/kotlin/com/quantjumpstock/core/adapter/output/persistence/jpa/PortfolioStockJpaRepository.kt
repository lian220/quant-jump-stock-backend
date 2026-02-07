package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PortfolioStockJpaRepository : JpaRepository<PortfolioStockEntity, Long> {

    fun findByPortfolioId(portfolioId: Long): List<PortfolioStockEntity>

    fun findByPortfolioIdAndStockId(portfolioId: Long, stockId: Long): PortfolioStockEntity?

    fun deleteByPortfolioId(portfolioId: Long)
}
