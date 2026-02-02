package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface FredIndicatorJpaRepository : JpaRepository<FredIndicatorEntity, Long> {

    fun findByCode(code: String): FredIndicatorEntity?

    fun findByIsActive(isActive: Boolean): List<FredIndicatorEntity>

    fun findByCategory(category: String): List<FredIndicatorEntity>

    @Query("SELECT f FROM FredIndicatorEntity f WHERE f.isActive = true ORDER BY f.code")
    fun findAllActiveIndicators(): List<FredIndicatorEntity>

    fun existsByCode(code: String): Boolean
}
