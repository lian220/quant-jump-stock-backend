package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface YfinanceIndicatorJpaRepository : JpaRepository<YfinanceIndicatorEntity, Long> {

    fun findByTicker(ticker: String): YfinanceIndicatorEntity?

    fun findByIsActive(isActive: Boolean): List<YfinanceIndicatorEntity>

    fun findByIndicatorType(indicatorType: String): List<YfinanceIndicatorEntity>

    @Query("SELECT y FROM YfinanceIndicatorEntity y WHERE y.isActive = true ORDER BY y.ticker")
    fun findAllActiveIndicators(): List<YfinanceIndicatorEntity>

    fun existsByTicker(ticker: String): Boolean
}
