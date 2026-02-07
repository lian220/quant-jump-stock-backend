package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    @Query(value = """
        SELECT * FROM stocks s
        WHERE (CAST(:query AS TEXT) IS NULL
            OR s.ticker ILIKE '%' || CAST(:query AS TEXT) || '%'
            OR s.stock_name ILIKE '%' || CAST(:query AS TEXT) || '%'
            OR (s.stock_name_en IS NOT NULL AND s.stock_name_en ILIKE '%' || CAST(:query AS TEXT) || '%'))
        AND (CAST(:market AS TEXT) IS NULL OR s.market = CAST(:market AS TEXT))
        AND (CAST(:sector AS TEXT) IS NULL OR s.sector = CAST(:sector AS TEXT))
        AND (:isActive IS NULL OR s.is_active = :isActive)
        ORDER BY s.ticker
    """, nativeQuery = true)
    fun search(
        @Param("query") query: String?,
        @Param("market") market: String?,
        @Param("sector") sector: String?,
        @Param("isActive") isActive: Boolean?,
        pageable: Pageable
    ): Page<StockEntity>
}
