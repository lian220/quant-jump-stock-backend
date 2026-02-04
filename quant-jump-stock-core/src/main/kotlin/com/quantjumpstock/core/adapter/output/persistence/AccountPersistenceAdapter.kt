package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.AccountBalanceEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.AccountBalanceJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.domain.model.trading.Account
import com.quantjumpstock.core.domain.port.output.AccountRepository
import org.springframework.stereotype.Component

/**
 * Account Persistence Adapter (Output Adapter)
 * AccountRepository 인터페이스를 구현하여 JPA와 연동합니다.
 */
@Component
class AccountPersistenceAdapter(
    private val accountBalanceJpaRepository: AccountBalanceJpaRepository,
    private val userJpaRepository: UserJpaRepository
) : AccountRepository {

    override fun save(account: Account): Account {
        val userEntity = userJpaRepository.findById(account.userId).orElseThrow {
            IllegalArgumentException("User not found: ${account.userId}")
        }
        val entity = toEntity(account, userEntity)
        val saved = accountBalanceJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): Account? {
        return accountBalanceJpaRepository.findById(id).orElse(null)?.let { toDomain(it) }
    }

    override fun findByUserId(userId: Long): Account? {
        return accountBalanceJpaRepository.findByUserId(userId).orElse(null)?.let { toDomain(it) }
    }

    override fun deleteById(id: Long) {
        accountBalanceJpaRepository.deleteById(id)
    }

    override fun existsByUserId(userId: Long): Boolean {
        return accountBalanceJpaRepository.existsByUserId(userId)
    }

    // ===== Mapping Functions =====

    private fun toDomain(entity: AccountBalanceEntity): Account {
        return Account(
            id = entity.id,
            userId = entity.user.id!!,
            cash = entity.cash,
            totalValue = entity.totalValue,
            lockedCash = entity.lockedCash,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun toEntity(domain: Account, userEntity: com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity): AccountBalanceEntity {
        return AccountBalanceEntity(
            id = domain.id,
            user = userEntity,
            cash = domain.cash,
            totalValue = domain.totalValue,
            lockedCash = domain.lockedCash,
            version = domain.version,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
