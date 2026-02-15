package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CollectorStateJpaRepository : JpaRepository<CollectorStateEntity, Long> {
    fun findBySource(source: String): CollectorStateEntity?
}
