package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserKisAccountJpaRepository : JpaRepository<UserKisAccountEntity, Long> {

    /**
     * 사용자 ID로 KIS 계정 조회
     */
    fun findByUserId(userId: Long): Optional<UserKisAccountEntity>

    /**
     * 사용자 userId(String)로 KIS 계정 조회
     */
    @Query("SELECT k FROM UserKisAccountEntity k JOIN k.user u WHERE u.userId = :userId")
    fun findByUserUserId(@Param("userId") userId: String): Optional<UserKisAccountEntity>

    /**
     * 활성화된 KIS 계정만 조회
     */
    @Query("SELECT k FROM UserKisAccountEntity k JOIN k.user u WHERE u.userId = :userId AND k.enabled = true")
    fun findActiveByUserUserId(@Param("userId") userId: String): Optional<UserKisAccountEntity>

    /**
     * 특정 계정 타입으로 조회
     */
    @Query("SELECT k FROM UserKisAccountEntity k WHERE k.user.id = :userId AND k.accountType = :accountType AND k.enabled = true")
    fun findByUserIdAndAccountType(
        @Param("userId") userId: Long,
        @Param("accountType") accountType: KisAccountType
    ): Optional<UserKisAccountEntity>
}
