package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.KisAccountType as JpaKisAccountType
import com.quantjumpstock.core.domain.model.user.KisAccountType
import com.quantjumpstock.core.domain.model.user.UserKisAccount
import com.quantjumpstock.core.domain.port.output.UserKisAccountRepository
import org.springframework.stereotype.Component

/**
 * UserKisAccount Persistence Adapter (Output Adapter)
 * UserKisAccountRepository 인터페이스를 구현하여 JPA와 연동합니다.
 */
@Component
class UserKisAccountPersistenceAdapter(
    private val kisAccountJpaRepository: UserKisAccountJpaRepository,
    private val userJpaRepository: UserJpaRepository
) : UserKisAccountRepository {

    override fun save(kisAccount: UserKisAccount): UserKisAccount {
        val entity = toEntity(kisAccount)
        val saved = kisAccountJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): UserKisAccount? {
        return kisAccountJpaRepository.findById(id).orElse(null)?.let { toDomain(it) }
    }

    override fun findByUserId(userId: Long): UserKisAccount? {
        return kisAccountJpaRepository.findByUserId(userId).orElse(null)?.let { toDomain(it) }
    }

    override fun findByUserUserId(userId: String): UserKisAccount? {
        return kisAccountJpaRepository.findByUserUserId(userId).orElse(null)?.let { toDomain(it) }
    }

    override fun findActiveByUserUserId(userId: String): UserKisAccount? {
        return kisAccountJpaRepository.findActiveByUserUserId(userId).orElse(null)?.let { toDomain(it) }
    }

    override fun findByUserIdAndAccountType(userId: Long, accountType: KisAccountType): UserKisAccount? {
        return kisAccountJpaRepository.findByUserIdAndAccountType(userId, mapAccountType(accountType))
            .orElse(null)?.let { toDomain(it) }
    }

    override fun deleteById(id: Long) {
        kisAccountJpaRepository.deleteById(id)
    }

    override fun existsByUserId(userId: Long): Boolean {
        return kisAccountJpaRepository.findByUserId(userId).isPresent
    }

    // ===== Mapping Functions =====

    private fun toDomain(entity: UserKisAccountEntity): UserKisAccount {
        return UserKisAccount(
            id = entity.id,
            userId = entity.user.id!!,
            appKey = entity.appKey,
            appSecretEncrypted = entity.appSecretEncrypted,
            accountNumber = entity.accountNumber,
            accountProductCode = entity.accountProductCode,
            accountType = mapAccountType(entity.accountType),
            enabled = entity.enabled,
            lastUsedAt = entity.lastUsedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun toEntity(domain: UserKisAccount): UserKisAccountEntity {
        val user = userJpaRepository.findById(domain.userId)
            .orElseThrow { IllegalArgumentException("User not found: ${domain.userId}") }

        return UserKisAccountEntity(
            id = domain.id,
            user = user,
            appKey = domain.appKey,
            appSecretEncrypted = domain.appSecretEncrypted,
            accountNumber = domain.accountNumber,
            accountProductCode = domain.accountProductCode,
            accountType = mapAccountType(domain.accountType),
            enabled = domain.enabled,
            lastUsedAt = domain.lastUsedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    private fun mapAccountType(jpaType: JpaKisAccountType): KisAccountType = when (jpaType) {
        JpaKisAccountType.REAL -> KisAccountType.REAL
        JpaKisAccountType.MOCK -> KisAccountType.MOCK
    }

    private fun mapAccountType(domainType: KisAccountType): JpaKisAccountType = when (domainType) {
        KisAccountType.REAL -> JpaKisAccountType.REAL
        KisAccountType.MOCK -> JpaKisAccountType.MOCK
    }
}
