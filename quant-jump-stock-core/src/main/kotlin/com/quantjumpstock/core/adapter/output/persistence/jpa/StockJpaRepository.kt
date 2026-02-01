package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface StockJpaRepository : JpaRepository<StockEntity, Long> {

    fun findByTicker(ticker: String): StockEntity?

    fun findByIsActive(isActive: Boolean): List<StockEntity>

    fun findByIsEtf(isEtf: Boolean): List<StockEntity>

    fun findBySector(sector: String): List<StockEntity>

    fun findByIndustry(industry: String): List<StockEntity>

    @Query("SELECT s FROM StockEntity s WHERE s.isActive = true ORDER BY s.ticker")
    fun findAllActiveStocks(): List<StockEntity>

    @Query("SELECT s FROM StockEntity s WHERE s.isActive = true AND s.isEtf = false ORDER BY s.ticker")
    fun findAllActiveNonEtfStocks(): List<StockEntity>

    @Query("SELECT s FROM StockEntity s WHERE s.isActive = true AND s.isEtf = true ORDER BY s.ticker")
    fun findAllActiveEtfs(): List<StockEntity>
}
