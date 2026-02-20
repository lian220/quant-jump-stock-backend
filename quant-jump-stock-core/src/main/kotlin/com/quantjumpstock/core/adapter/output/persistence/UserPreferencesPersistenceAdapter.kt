package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserPreferencesEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserPreferencesJpaRepository
import com.quantjumpstock.core.domain.model.user.UserPreferences
import com.quantjumpstock.core.domain.port.output.UserPreferencesRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * UserPreferences Persistence Adapter (Output Adapter)
 */
@Component
class UserPreferencesPersistenceAdapter(
    private val jpaRepository: UserPreferencesJpaRepository
) : UserPreferencesRepository {

    @Transactional
    override fun save(preferences: UserPreferences): UserPreferences {
        // 기존 레코드가 있으면 ID를 가져와서 업데이트
        val existingId = jpaRepository.findByUserId(preferences.userId)
            .orElse(null)?.id

        val entity = toEntity(preferences.copy(id = existingId ?: preferences.id))
        val saved = jpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findByUserId(userId: Long): UserPreferences? {
        return jpaRepository.findByUserId(userId).orElse(null)?.let { toDomain(it) }
    }

    override fun findByUserIdIn(userIds: List<Long>): List<UserPreferences> {
        return jpaRepository.findByUserIdIn(userIds).map { toDomain(it) }
    }

    // ===== Mapping Functions =====

    private fun toDomain(entity: UserPreferencesEntity): UserPreferences {
        return UserPreferences(
            id = entity.id,
            userId = entity.userId,
            investmentCategories = entity.investmentCategories
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList(),
            markets = entity.markets
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList(),
            riskTolerance = entity.riskTolerance,
            onboardingCompleted = entity.onboardingCompleted
        )
    }

    private fun toEntity(domain: UserPreferences): UserPreferencesEntity {
        return UserPreferencesEntity(
            id = domain.id,
            userId = domain.userId,
            investmentCategories = domain.investmentCategories.joinToString(",").ifEmpty { null },
            markets = domain.markets.joinToString(",").ifEmpty { null },
            riskTolerance = domain.riskTolerance,
            onboardingCompleted = domain.onboardingCompleted
        )
    }
}
