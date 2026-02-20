package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PortfolioStockJpaRepository : JpaRepository<PortfolioStockEntity, Long> {

    fun findByPortfolioId(portfolioId: Long): List<PortfolioStockEntity>

    fun findByPortfolioIdAndStockId(portfolioId: Long, stockId: Long): PortfolioStockEntity?

    @Query("SELECT ps.portfolioId, COUNT(ps) FROM PortfolioStockEntity ps WHERE ps.portfolioId IN :portfolioIds GROUP BY ps.portfolioId")
    fun countGroupByPortfolioId(portfolioIds: List<Long>): List<Array<Any>>

    fun deleteByPortfolioId(portfolioId: Long)
}
