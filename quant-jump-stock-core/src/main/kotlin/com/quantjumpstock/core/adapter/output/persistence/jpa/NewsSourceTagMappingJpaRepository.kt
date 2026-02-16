package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface NewsSourceTagMappingJpaRepository : JpaRepository<NewsSourceTagMappingEntity, Long> {
    fun findBySourceAndSourceTag(source: String, sourceTag: String): NewsSourceTagMappingEntity?
    fun findBySource(source: String): List<NewsSourceTagMappingEntity>
}
