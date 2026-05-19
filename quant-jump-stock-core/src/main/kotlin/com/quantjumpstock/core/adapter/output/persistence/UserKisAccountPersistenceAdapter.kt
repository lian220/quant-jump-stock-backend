package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.KisAccountType as JpaKisAccountType
import com.quantjumpstock.core.domain.model.user.KisAccountType
import com.quantjumpstock.core.domain.model.user.UserKisAccount
import com.quantjumpstock.core.domain.port.output.UserKisAccountRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * UserKisAccount Persistence Adapter (Output Adapter)
 * UserKisAccountRepository 인터페이스를 구현하여 JPA와 연동합니다.
 *
 * A+ 모델: 활성/휴지통 row 모두 영속 가능. 일반 조회는 활성만 반환.
 */
@Component
class UserKisAccountPersistenceAdapter(
    private val kisAccountJpaRepository: UserKisAccountJpaRepository,
    private val userJpaRepository: UserJpaRepository
) : UserKisAccountRepository {

    override fun save(kisAccount: UserKisAccount): UserKisAccount {
        // saveAndFlush 사용 이유: A+ 모델에서 softDelete + 새 row INSERT 가 동일 트랜잭션 안에
        // 일어날 때, partial unique index `(user_id) WHERE deleted_at IS NULL` 가 잠시라도
        // 활성 row 가 두 개로 보이면 위반된다. flush 로 SQL 순서를 강제하여 충돌 방지.
        val entity = toEntity(kisAccount)
        val saved = kisAccountJpaRepository.saveAndFlush(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): UserKisAccount? {
        return kisAccountJpaRepository.findById(id).orElse(null)?.let { toDomain(it) }
    }

    override fun findByUserId(userId: Long): UserKisAccount? {
        return kisAccountJpaRepository.findActiveByUserId(userId).orElse(null)?.let { toDomain(it) }
    }

    override fun findByUserUserId(userId: String): UserKisAccount? {
        return kisAccountJpaRepository.findActiveByUserUserId(userId).orElse(null)?.let { toDomain(it) }
    }

    override fun findActiveByUserUserId(userId: String): UserKisAccount? {
        return kisAccountJpaRepository.findActiveByUserUserId(userId).orElse(null)?.let { toDomain(it) }
    }

    override fun findTrashedByUserUserId(userId: String): UserKisAccount? {
        return kisAccountJpaRepository.findTrashedByUserUserId(userId).firstOrNull()?.let { toDomain(it) }
    }

    override fun findExpiredTrashed(thresholdAt: LocalDateTime): List<UserKisAccount> {
        return kisAccountJpaRepository.findExpiredTrashed(thresholdAt).map { toDomain(it) }
    }

    override fun findByUserIdAndAccountType(userId: Long, accountType: KisAccountType): UserKisAccount? {
        return kisAccountJpaRepository.findByUserIdAndAccountType(userId, mapAccountType(accountType))
            .orElse(null)?.let { toDomain(it) }
    }

    override fun deleteById(id: Long) {
        kisAccountJpaRepository.deleteById(id)
        // evictTrashIfPresent → softDelete → insert 순서에서 partial unique 위반 방지.
        kisAccountJpaRepository.flush()
    }

    override fun existsByUserId(userId: Long): Boolean {
        return kisAccountJpaRepository.findActiveByUserId(userId).isPresent
    }

    // ===== Phase 1B (V63) 신규 — account_type 별 조회 =====

    override fun findActiveByUserIdAndType(userId: Long, accountType: KisAccountType): UserKisAccount? {
        return kisAccountJpaRepository
            .findActiveByUserIdAndType(userId, mapAccountType(accountType))
            .orElse(null)?.let { toDomain(it) }
    }

    override fun findActiveByUserUserIdAndType(userId: String, accountType: KisAccountType): UserKisAccount? {
        return kisAccountJpaRepository
            .findActiveByUserUserIdAndType(userId, mapAccountType(accountType))
            .orElse(null)?.let { toDomain(it) }
    }

    override fun findAllActiveByUserId(userId: Long): List<UserKisAccount> {
        return kisAccountJpaRepository.findAllActiveByUserId(userId).map { toDomain(it) }
    }

    override fun findAllActiveByUserUserId(userId: String): List<UserKisAccount> {
        return kisAccountJpaRepository.findAllActiveByUserUserId(userId).map { toDomain(it) }
    }

    override fun findTrashedByUserIdAndType(userId: Long, accountType: KisAccountType): UserKisAccount? {
        return kisAccountJpaRepository
            .findTrashedByUserIdAndType(userId, mapAccountType(accountType))
            .firstOrNull()?.let { toDomain(it) }
    }

    override fun findTrashedByUserUserIdAndType(userId: String, accountType: KisAccountType): UserKisAccount? {
        return kisAccountJpaRepository
            .findTrashedByUserUserIdAndType(userId, mapAccountType(accountType))
            .firstOrNull()?.let { toDomain(it) }
    }

    // ===== Mapping Functions =====

    private fun toDomain(entity: UserKisAccountEntity): UserKisAccount {
        return UserKisAccount(
            id = entity.id,
            userId = entity.user.id!!,
            appKey = entity.appKey,
            appSecretEncrypted = entity.appSecretEncrypted,
            appSecretEncryptedV2 = entity.appSecretEncryptedV2,
            accountNumber = entity.accountNumber,
            accountProductCode = entity.accountProductCode,
            accountType = mapAccountType(entity.accountType),
            enabled = entity.enabled,
            lastUsedAt = entity.lastUsedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt
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
            appSecretEncryptedV2 = domain.appSecretEncryptedV2,
            accountNumber = domain.accountNumber,
            accountProductCode = domain.accountProductCode,
            accountType = mapAccountType(domain.accountType),
            enabled = domain.enabled,
            lastUsedAt = domain.lastUsedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            deletedAt = domain.deletedAt
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
