package com.quantjumpstock.core.adapter.output.persistence.jpa.mapper

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserStatus as JpaUserStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserRole as JpaUserRole
import com.quantjumpstock.core.domain.model.user.User
import com.quantjumpstock.core.domain.model.user.UserStatus
import com.quantjumpstock.core.domain.model.user.UserRole
import org.springframework.stereotype.Component

/**
 * User Mapper - JPA Entity ↔ Domain Model 변환
 */
@Component
class UserMapper {

    /**
     * JPA Entity → Domain Model
     */
    fun toDomain(entity: UserEntity): User {
        return User(
            id = entity.id,
            userId = entity.userId,
            name = entity.name,
            email = entity.email,
            profileImageUrl = entity.profileImageUrl,
            status = mapStatus(entity.status),
            role = mapRole(entity.role),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    /**
     * Domain Model → JPA Entity
     */
    fun toEntity(domain: User): UserEntity {
        return UserEntity(
            id = domain.id,
            userId = domain.userId,
            name = domain.name,
            email = domain.email,
            profileImageUrl = domain.profileImageUrl,
            status = mapStatusToJpa(domain.status),
            role = mapRoleToJpa(domain.role),
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    private fun mapStatus(jpaStatus: JpaUserStatus): UserStatus {
        return when (jpaStatus) {
            JpaUserStatus.ACTIVE -> UserStatus.ACTIVE
            JpaUserStatus.INACTIVE -> UserStatus.INACTIVE
            JpaUserStatus.SUSPENDED -> UserStatus.SUSPENDED
        }
    }

    private fun mapStatusToJpa(domainStatus: UserStatus): JpaUserStatus {
        return when (domainStatus) {
            UserStatus.ACTIVE -> JpaUserStatus.ACTIVE
            UserStatus.INACTIVE -> JpaUserStatus.INACTIVE
            UserStatus.SUSPENDED -> JpaUserStatus.SUSPENDED
        }
    }

    private fun mapRole(jpaRole: JpaUserRole): UserRole {
        return when (jpaRole) {
            JpaUserRole.ADMIN -> UserRole.ADMIN
            JpaUserRole.USER -> UserRole.USER
            JpaUserRole.MODERATOR -> UserRole.MODERATOR
        }
    }

    private fun mapRoleToJpa(domainRole: UserRole): JpaUserRole {
        return when (domainRole) {
            UserRole.ADMIN -> JpaUserRole.ADMIN
            UserRole.USER -> JpaUserRole.USER
            UserRole.MODERATOR -> JpaUserRole.MODERATOR
        }
    }
}
