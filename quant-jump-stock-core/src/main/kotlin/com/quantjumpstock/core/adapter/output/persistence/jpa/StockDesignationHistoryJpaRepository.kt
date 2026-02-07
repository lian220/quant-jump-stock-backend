package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StockDesignationHistoryJpaRepository : JpaRepository<StockDesignationHistoryEntity, Long> {

    fun findByStockIdOrderByChangedAtDesc(stockId: Long): List<StockDesignationHistoryEntity>
}
