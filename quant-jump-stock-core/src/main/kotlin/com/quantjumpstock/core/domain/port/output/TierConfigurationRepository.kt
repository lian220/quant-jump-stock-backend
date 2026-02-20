package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.tier.TierConfiguration

interface TierConfigurationRepository {
    fun findByTier(tier: String): TierConfiguration?
    fun findAll(): List<TierConfiguration>
    fun save(config: TierConfiguration): TierConfiguration
}
