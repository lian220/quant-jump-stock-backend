package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StrategyCategoryRepository : JpaRepository<StrategyCategoryEntity, Long> {

    fun findByCode(code: String): StrategyCategoryEntity?

    fun findByIsActiveTrueOrderBySortOrderAsc(): List<StrategyCategoryEntity>

    fun existsByCode(code: String): Boolean
}
