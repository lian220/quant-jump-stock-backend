package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface KisTokenJpaRepository : JpaRepository<KisTokenEntity, Long> {

    /**
     * 사용자 ID와 계정 타입으로 최신 유효한 토큰 조회
     */
    @Query("""
        SELECT t FROM KisTokenEntity t
        JOIN t.user u
        WHERE u.userId = :userId
        AND t.accountType = :accountType
        AND t.isActive = true
        ORDER BY t.createdAt DESC
        LIMIT 1
    """)
    fun findLatestTokenByUserIdAndAccountType(
        @Param("userId") userId: String,
        @Param("accountType") accountType: KisAccountType
    ): Optional<KisTokenEntity>

    /**
     * 사용자 ID와 계정 타입으로 모든 토큰 조회
     */
    @Query("""
        SELECT t FROM KisTokenEntity t
        JOIN t.user u
        WHERE u.userId = :userId
        AND t.accountType = :accountType
    """)
    fun findAllByUserIdAndAccountType(
        @Param("userId") userId: String,
        @Param("accountType") accountType: KisAccountType
    ): List<KisTokenEntity>

    /**
     * 만료된 토큰 비활성화
     */
    @Modifying
    @Query("""
        UPDATE KisTokenEntity t
        SET t.isActive = false, t.updatedAt = :now
        WHERE t.expirationTime < :now
        AND t.isActive = true
    """)
    fun deactivateExpiredTokens(@Param("now") now: LocalDateTime): Int

    /**
     * 사용자의 특정 계정 타입 토큰 모두 비활성화 (새 토큰 발급 시 이전 토큰 무효화)
     */
    @Modifying
    @Query("""
        UPDATE KisTokenEntity t
        SET t.isActive = false, t.updatedAt = :now
        WHERE t.user.id = :userId
        AND t.accountType = :accountType
    """)
    fun deactivateUserTokens(
        @Param("userId") userId: Long,
        @Param("accountType") accountType: KisAccountType,
        @Param("now") now: LocalDateTime
    ): Int

    /**
     * 사용자 ID로 모든 토큰 삭제 (사용자 삭제 시)
     */
    fun deleteByUserId(userId: Long)
}
