package com.quantjumpstock.core.adapter.output.persistence.jpa.adapter

import com.quantjumpstock.core.adapter.output.persistence.jpa.AccountBalanceJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.mapper.AccountMapper
import com.quantjumpstock.core.domain.model.trading.Account
import com.quantjumpstock.core.domain.port.output.AccountRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

/**
 * Account Persistence Adapter - Hexagonal Architecture 준수
 */
@Repository
class AccountPersistenceAdapter(
    private val accountBalanceJpaRepository: AccountBalanceJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val accountMapper: AccountMapper
) : AccountRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun save(account: Account): Account {
        logger.debug("계좌 저장: userId=${account.userId}")

        val entity = if (account.id != null) {
            // 기존 엔티티 업데이트
            val existingEntity = accountBalanceJpaRepository.findById(account.id)
                .orElseThrow { IllegalArgumentException("Account not found: ${account.id}") }
            accountMapper.updateEntity(existingEntity, account)
        } else {
            // 새 엔티티 생성
            val userEntity = userJpaRepository.findById(account.userId)
                .orElseThrow { IllegalArgumentException("User not found: ${account.userId}") }
            accountMapper.toEntity(account, userEntity)
        }

        val savedEntity = accountBalanceJpaRepository.save(entity)
        return accountMapper.toDomain(savedEntity)
    }

    override fun findById(id: Long): Account? {
        logger.debug("계좌 조회: id=$id")
        return accountBalanceJpaRepository.findById(id)
            .map { accountMapper.toDomain(it) }
            .orElse(null)
    }

    override fun findByUserId(userId: Long): Account? {
        logger.debug("사용자별 계좌 조회: userId=$userId")
        return accountBalanceJpaRepository.findByUserIdNullable(userId)
            ?.let { accountMapper.toDomain(it) }
    }

    override fun deleteById(id: Long) {
        logger.debug("계좌 삭제: id=$id")
        accountBalanceJpaRepository.deleteById(id)
    }

    override fun existsByUserId(userId: Long): Boolean {
        return accountBalanceJpaRepository.existsByUserId(userId)
    }
}
