package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.TierConfigurationEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.TierConfigurationJpaRepository
import com.quantjumpstock.core.domain.model.tier.TierConfiguration
import com.quantjumpstock.core.domain.port.output.TierConfigurationRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TierConfigurationPersistenceAdapter(
    private val jpaRepository: TierConfigurationJpaRepository
) : TierConfigurationRepository {

    override fun findByTier(tier: String): TierConfiguration? {
        return jpaRepository.findByTier(tier).orElse(null)?.toDomain()
    }

    override fun findAll(): List<TierConfiguration> {
        return jpaRepository.findAll().map { it.toDomain() }
    }

    @Transactional
    override fun save(config: TierConfiguration): TierConfiguration {
        val entity = jpaRepository.findByTier(config.tier).orElse(null)
        return if (entity != null) {
            entity.updateFrom(config)
            jpaRepository.save(entity).toDomain()
        } else {
            val newEntity = TierConfigurationEntity(
                tier = config.tier,
                maxSubscriptionCount = config.maxSubscriptionCount,
                maxBacktestDaily = config.maxBacktestDaily,
                maxBacktestPerStrategy = config.maxBacktestPerStrategy,
                isUnlimitedSubscription = config.isUnlimitedSubscription,
                isUnlimitedBacktest = config.isUnlimitedBacktest,
                description = config.description,
                updatedBy = config.updatedBy
            )
            jpaRepository.save(newEntity).toDomain()
        }
    }
}
