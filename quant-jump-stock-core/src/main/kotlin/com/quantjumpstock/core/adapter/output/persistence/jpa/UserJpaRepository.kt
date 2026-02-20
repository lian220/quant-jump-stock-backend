package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserJpaRepository : JpaRepository<UserEntity, Long> {

    fun findByUserId(userId: String): Optional<UserEntity>

    fun findByEmail(email: String): Optional<UserEntity>

    fun findByStatus(status: UserStatus): List<UserEntity>

    @Query("""
        SELECT u FROM UserEntity u
        JOIN FETCH u.tradingConfig tc
        WHERE tc.enabled = true AND tc.autoTradingEnabled = true
    """)
    fun findUsersWithAutoTradingEnabled(): List<UserEntity>

    @Query("""
        SELECT u FROM UserEntity u
        LEFT JOIN FETCH u.tradingConfig
        LEFT JOIN FETCH u.accountBalance
        WHERE u.userId = :userId
    """)
    fun findByUserIdWithDetails(userId: String): Optional<UserEntity>

    fun existsByUserId(userId: String): Boolean

    fun existsByEmail(email: String): Boolean

    // OAuth 관련 쿼리 (V14)
    fun findByOauthProviderAndOauthProviderId(
        oauthProvider: OAuthProvider,
        oauthProviderId: String
    ): UserEntity?

    // 관리자용 검색+필터 쿼리
    @Query("""
        SELECT u FROM UserEntity u
        WHERE (LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
          AND u.status = :status
    """)
    fun findBySearchAndStatus(
        @Param("search") search: String,
        @Param("status") status: UserStatus,
        pageable: Pageable
    ): Page<UserEntity>

    @Query("""
        SELECT u FROM UserEntity u
        WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
    """)
    fun findBySearch(
        @Param("search") search: String,
        pageable: Pageable
    ): Page<UserEntity>

    fun findByStatus(status: UserStatus, pageable: Pageable): Page<UserEntity>

    fun countByStatus(status: UserStatus): Long
}
