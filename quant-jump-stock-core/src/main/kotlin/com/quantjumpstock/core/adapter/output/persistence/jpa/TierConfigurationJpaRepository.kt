package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface TierConfigurationJpaRepository : JpaRepository<TierConfigurationEntity, Long> {
    fun findByTier(tier: String): Optional<TierConfigurationEntity>
}
