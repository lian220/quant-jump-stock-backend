package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface NewsCategoryJpaRepository : JpaRepository<NewsCategoryEntity, Long> {
    fun findByIsActiveTrue(): List<NewsCategoryEntity>
    fun findByName(name: String): NewsCategoryEntity?
}
