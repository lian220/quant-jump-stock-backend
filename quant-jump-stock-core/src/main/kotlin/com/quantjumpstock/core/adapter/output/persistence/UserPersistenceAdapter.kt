package com.quantjumpstock.core.adapter.output.persistence

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.OAuthProvider as JpaOAuthProvider
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserStatus as JpaUserStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserRole as JpaUserRole
import com.quantjumpstock.core.domain.model.user.OAuthProvider
import com.quantjumpstock.core.domain.model.user.User
import com.quantjumpstock.core.domain.model.user.UserRole
import com.quantjumpstock.core.domain.model.user.UserStatus
import com.quantjumpstock.core.domain.port.output.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * User Persistence Adapter (Output Adapter)
 * UserRepository 인터페이스를 구현하여 JPA와 연동합니다.
 */
@Component
class UserPersistenceAdapter(
    private val userJpaRepository: UserJpaRepository
) : UserRepository {

    @Transactional
    override fun save(user: User): User {
        val entity = toEntity(user)
        val saved = userJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): User? {
        return userJpaRepository.findById(id).orElse(null)?.let { toDomain(it) }
    }

    override fun findByUserId(userId: String): User? {
        return userJpaRepository.findByUserId(userId).orElse(null)?.let { toDomain(it) }
    }

    override fun findByEmail(email: String): User? {
        return userJpaRepository.findByEmail(email).orElse(null)?.let { toDomain(it) }
    }

    override fun existsById(id: Long): Boolean {
        return userJpaRepository.existsById(id)
    }

    override fun existsByUserId(userId: String): Boolean {
        return userJpaRepository.existsByUserId(userId)
    }

    override fun existsByEmail(email: String): Boolean {
        return userJpaRepository.existsByEmail(email)
    }

    override fun deleteById(id: Long) {
        userJpaRepository.deleteById(id)
    }

    override fun findByOAuthProviderAndProviderId(provider: OAuthProvider, providerId: String): User? {
        return userJpaRepository.findByOauthProviderAndOauthProviderId(
            mapOAuthProvider(provider),
            providerId
        )?.let { toDomain(it) }
    }

    override fun findAllPaged(page: Int, size: Int, search: String?, status: UserStatus?): Pair<List<User>, Long> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val jpaStatus = status?.let { mapStatus(it) }
        val searchTerm = search?.takeIf { it.isNotBlank() }

        val result = when {
            searchTerm != null && jpaStatus != null ->
                userJpaRepository.findBySearchAndStatus(searchTerm, jpaStatus, pageable)
            searchTerm != null ->
                userJpaRepository.findBySearch(searchTerm, pageable)
            jpaStatus != null ->
                userJpaRepository.findByStatus(jpaStatus, pageable)
            else ->
                userJpaRepository.findAll(pageable)
        }

        return Pair(result.content.map { toDomain(it) }, result.totalElements)
    }

    override fun countByStatus(status: UserStatus): Long {
        return userJpaRepository.countByStatus(mapStatus(status))
    }

    override fun count(): Long {
        return userJpaRepository.count()
    }

    // ===== Mapping Functions =====

    private fun toDomain(entity: UserEntity): User {
        return User(
            id = entity.id,
            userId = entity.userId,
            name = entity.name,
            email = entity.email,
            passwordHash = entity.passwordHash,
            oauthProvider = entity.oauthProvider?.let { mapOAuthProvider(it) },
            oauthProviderId = entity.oauthProviderId,
            profileImageUrl = entity.profileImageUrl,
            phone = entity.phone,
            status = mapStatus(entity.status),
            role = mapRole(entity.role),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun toEntity(domain: User): UserEntity {
        return UserEntity(
            id = domain.id,
            userId = domain.userId,
            name = domain.name,
            email = domain.email,
            passwordHash = domain.passwordHash,
            oauthProvider = domain.oauthProvider?.let { mapOAuthProvider(it) },
            oauthProviderId = domain.oauthProviderId,
            profileImageUrl = domain.profileImageUrl,
            phone = domain.phone,
            status = mapStatus(domain.status),
            role = mapRole(domain.role),
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    private fun mapStatus(status: JpaUserStatus): UserStatus = when (status) {
        JpaUserStatus.ACTIVE -> UserStatus.ACTIVE
        JpaUserStatus.INACTIVE -> UserStatus.INACTIVE
        JpaUserStatus.SUSPENDED -> UserStatus.SUSPENDED
    }

    private fun mapStatus(status: UserStatus): JpaUserStatus = when (status) {
        UserStatus.ACTIVE -> JpaUserStatus.ACTIVE
        UserStatus.INACTIVE -> JpaUserStatus.INACTIVE
        UserStatus.SUSPENDED -> JpaUserStatus.SUSPENDED
    }

    private fun mapRole(role: JpaUserRole): UserRole = when (role) {
        JpaUserRole.ADMIN -> UserRole.ADMIN
        JpaUserRole.USER -> UserRole.USER
        JpaUserRole.MODERATOR -> UserRole.MODERATOR
    }

    private fun mapRole(role: UserRole): JpaUserRole = when (role) {
        UserRole.ADMIN -> JpaUserRole.ADMIN
        UserRole.USER -> JpaUserRole.USER
        UserRole.MODERATOR -> JpaUserRole.MODERATOR
    }

    private fun mapOAuthProvider(provider: JpaOAuthProvider): OAuthProvider = when (provider) {
        JpaOAuthProvider.GOOGLE -> OAuthProvider.GOOGLE
        JpaOAuthProvider.NAVER -> OAuthProvider.NAVER
    }

    private fun mapOAuthProvider(provider: OAuthProvider): JpaOAuthProvider = when (provider) {
        OAuthProvider.GOOGLE -> JpaOAuthProvider.GOOGLE
        OAuthProvider.NAVER -> JpaOAuthProvider.NAVER
    }
}
