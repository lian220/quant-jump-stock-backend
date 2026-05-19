package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.AccountTypeEntityEnum
import com.quantjumpstock.core.adapter.output.persistence.jpa.BrokerEntityEnum
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserBrokerAccountEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserBrokerAccountJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.domain.model.broker.AccountType
import com.quantjumpstock.core.domain.model.broker.Broker
import com.quantjumpstock.core.domain.model.broker.BrokerCredentials
import com.quantjumpstock.core.domain.model.broker.UserBrokerAccount
import com.quantjumpstock.core.domain.port.output.UserBrokerAccountRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * UserBrokerAccount Persistence Adapter (Phase 1B v2.1).
 *
 * Hexagonal layer 격리: domain ↔ JPA 사이 모든 변환은 본 클래스에서.
 * - domain `Broker` ↔ JPA `BrokerEntityEnum`
 * - domain `AccountType` ↔ JPA `AccountTypeEntityEnum`
 * - domain `BrokerCredentials` (sealed) ↔ JPA `app_key` + `app_secret_encrypted` 컬럼
 *
 * partial unique 충돌 회피: `saveAndFlush` 로 SQL 순서 강제 (softDelete + insert 동일 트랜잭션).
 */
@Component
class UserBrokerAccountPersistenceAdapter(
    private val jpaRepository: UserBrokerAccountJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : UserBrokerAccountRepository {

    override fun save(account: UserBrokerAccount): UserBrokerAccount {
        val entity = toEntity(account)
        val saved = jpaRepository.saveAndFlush(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): UserBrokerAccount? =
        jpaRepository.findById(id).orElse(null)?.let { toDomain(it) }

    override fun findAllActiveByUserId(userId: Long): List<UserBrokerAccount> =
        jpaRepository.findAllActiveByUserId(userId).map { toDomain(it) }

    override fun findActiveByUserIdAndKey(
        userId: Long,
        broker: Broker,
        accountType: AccountType,
        accountNumber: String,
    ): UserBrokerAccount? =
        jpaRepository.findActiveByUserIdAndKey(
            userId,
            toEntityBroker(broker),
            toEntityAccountType(accountType),
            accountNumber,
        ).orElse(null)?.let { toDomain(it) }

    override fun findAllTrashedByUserId(userId: Long): List<UserBrokerAccount> =
        jpaRepository.findAllTrashedByUserId(userId).map { toDomain(it) }

    override fun findExpiredTrashed(thresholdAt: LocalDateTime): List<UserBrokerAccount> =
        jpaRepository.findExpiredTrashed(thresholdAt).map { toDomain(it) }

    override fun findFirstActiveKisByUserLoginId(loginUserId: String): UserBrokerAccount? =
        jpaRepository.findFirstActiveKisByUserLoginId(loginUserId).orElse(null)?.let { toDomain(it) }

    override fun deleteById(id: Long) {
        jpaRepository.deleteById(id)
        jpaRepository.flush()
    }

    // ===== Mapping =====

    private fun toDomain(entity: UserBrokerAccountEntity): UserBrokerAccount {
        val broker = toDomainBroker(entity.broker)
        return UserBrokerAccount(
            id = entity.id,
            userId = entity.user.id!!,
            broker = broker,
            accountType = toDomainAccountType(entity.accountType),
            accountNumber = entity.accountNumber,
            accountProductCode = entity.accountProductCode,
            accountAlias = entity.accountAlias,
            credentials = toDomainCredentials(broker, entity.appKey, entity.appSecretEncrypted),
            enabled = entity.enabled,
            lastUsedAt = entity.lastUsedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt,
        )
    }

    private fun toEntity(domain: UserBrokerAccount): UserBrokerAccountEntity {
        val user = userJpaRepository.findById(domain.userId)
            .orElseThrow { IllegalArgumentException("User not found: ${domain.userId}") }
        return UserBrokerAccountEntity(
            id = domain.id,
            user = user,
            broker = toEntityBroker(domain.broker),
            accountType = toEntityAccountType(domain.accountType),
            accountNumber = domain.accountNumber,
            accountProductCode = domain.accountProductCode,
            accountAlias = domain.accountAlias,
            appKey = domain.credentials.externalKey,
            appSecretEncrypted = domain.credentials.externalSecretEncrypted,
            enabled = domain.enabled,
            lastUsedAt = domain.lastUsedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            deletedAt = domain.deletedAt,
        )
    }

    private fun toDomainBroker(e: BrokerEntityEnum): Broker = when (e) {
        BrokerEntityEnum.KIS -> Broker.KIS
        BrokerEntityEnum.TOSS -> Broker.TOSS
    }

    private fun toEntityBroker(d: Broker): BrokerEntityEnum = when (d) {
        Broker.KIS -> BrokerEntityEnum.KIS
        Broker.TOSS -> BrokerEntityEnum.TOSS
    }

    private fun toDomainAccountType(e: AccountTypeEntityEnum): AccountType = when (e) {
        AccountTypeEntityEnum.MOCK -> AccountType.MOCK
        AccountTypeEntityEnum.REAL -> AccountType.REAL
    }

    private fun toEntityAccountType(d: AccountType): AccountTypeEntityEnum = when (d) {
        AccountType.MOCK -> AccountTypeEntityEnum.MOCK
        AccountType.REAL -> AccountTypeEntityEnum.REAL
    }

    /**
     * DB 의 broker-agnostic 컬럼 `app_key` + `app_secret_encrypted` 를
     * broker 에 따라 해당 `BrokerCredentials` 변종으로 디코딩.
     *
     * 컬럼 의미는 broker 마다 재해석:
     * - KIS:  app_key=KIS appKey,  app_secret_encrypted=GCM(KIS appSecret)
     * - TOSS: app_key=client_id,   app_secret_encrypted=GCM(refresh_token)
     */
    private fun toDomainCredentials(
        broker: Broker,
        appKey: String,
        appSecretEncrypted: String,
    ): BrokerCredentials = when (broker) {
        Broker.KIS -> BrokerCredentials.Kis(appKey = appKey, appSecretEncrypted = appSecretEncrypted)
        Broker.TOSS -> BrokerCredentials.TossOAuth(clientId = appKey, refreshTokenEncrypted = appSecretEncrypted)
    }
}
