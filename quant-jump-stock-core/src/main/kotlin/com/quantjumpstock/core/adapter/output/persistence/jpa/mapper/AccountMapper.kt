package com.quantjumpstock.core.adapter.output.persistence.jpa.mapper

import com.quantjumpstock.core.adapter.output.persistence.jpa.AccountBalanceEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity
import com.quantjumpstock.core.domain.model.trading.Account
import org.springframework.stereotype.Component

/**
 * Account Mapper - JPA Entity ↔ Domain Model 변환
 */
@Component
class AccountMapper {

    /**
     * JPA Entity → Domain Model
     */
    fun toDomain(entity: AccountBalanceEntity): Account {
        return Account(
            id = entity.id,
            userId = entity.user.id ?: throw IllegalStateException("User ID is null"),
            cash = entity.cash,
            totalValue = entity.totalValue,
            lockedCash = entity.lockedCash,
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    /**
     * Domain Model → JPA Entity (새 엔티티 생성용)
     *
     * @param domain 도메인 모델
     * @param user 연관된 사용자 엔티티 (이미 조회된 상태)
     */
    fun toEntity(domain: Account, user: UserEntity): AccountBalanceEntity {
        return AccountBalanceEntity(
            id = domain.id,
            user = user,
            cash = domain.cash,
            totalValue = domain.totalValue,
            lockedCash = domain.lockedCash,
            version = domain.version,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    /**
     * 기존 Entity 업데이트용
     */
    fun updateEntity(entity: AccountBalanceEntity, domain: Account): AccountBalanceEntity {
        entity.cash = domain.cash
        entity.totalValue = domain.totalValue
        entity.lockedCash = domain.lockedCash
        entity.updatedAt = domain.updatedAt
        return entity
    }
}
