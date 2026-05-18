package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface UserKisAccountJpaRepository : JpaRepository<UserKisAccountEntity, Long> {

    /**
     * 사용자 ID로 활성 KIS 계정 조회. 휴지통 row 는 제외.
     */
    @Query("SELECT k FROM UserKisAccountEntity k WHERE k.user.id = :userId AND k.deletedAt IS NULL")
    fun findActiveByUserId(@Param("userId") userId: Long): Optional<UserKisAccountEntity>

    /**
     * 사용자 userId(String)로 활성 KIS 계정 조회. 휴지통 row 는 제외.
     */
    @Query("SELECT k FROM UserKisAccountEntity k JOIN k.user u WHERE u.userId = :userId AND k.deletedAt IS NULL")
    fun findActiveByUserUserId(@Param("userId") userId: String): Optional<UserKisAccountEntity>

    /**
     * @deprecated A+ 도입 후 `findActiveByUserUserId` 로 대체. 기존 테스트 호환성을 위해 유지.
     */
    @Deprecated("Use findActiveByUserUserId", ReplaceWith("findActiveByUserUserId(userId)"))
    @Query("SELECT k FROM UserKisAccountEntity k JOIN k.user u WHERE u.userId = :userId AND k.deletedAt IS NULL")
    fun findByUserUserId(@Param("userId") userId: String): Optional<UserKisAccountEntity>

    /**
     * 사용자 ID로 휴지통 row 조회 (가장 최근 1개).
     */
    @Query("SELECT k FROM UserKisAccountEntity k WHERE k.user.id = :userId AND k.deletedAt IS NOT NULL ORDER BY k.deletedAt DESC")
    fun findTrashedByUserId(@Param("userId") userId: Long): List<UserKisAccountEntity>

    /**
     * 사용자 userId(String)로 휴지통 row 조회 (가장 최근 1개).
     */
    @Query("SELECT k FROM UserKisAccountEntity k JOIN k.user u WHERE u.userId = :userId AND k.deletedAt IS NOT NULL ORDER BY k.deletedAt DESC")
    fun findTrashedByUserUserId(@Param("userId") userId: String): List<UserKisAccountEntity>

    /**
     * 만료된 휴지통 row (Scheduler hard delete 대상).
     */
    @Query("SELECT k FROM UserKisAccountEntity k WHERE k.deletedAt IS NOT NULL AND k.deletedAt < :thresholdAt")
    fun findExpiredTrashed(@Param("thresholdAt") thresholdAt: LocalDateTime): List<UserKisAccountEntity>

    /**
     * 특정 계정 타입의 활성 row 조회.
     */
    @Query("SELECT k FROM UserKisAccountEntity k WHERE k.user.id = :userId AND k.accountType = :accountType AND k.deletedAt IS NULL")
    fun findByUserIdAndAccountType(
        @Param("userId") userId: Long,
        @Param("accountType") accountType: KisAccountType
    ): Optional<UserKisAccountEntity>

    /**
     * V2(GCM) backfill 대상 row 의 PK 만 조회. entity full load 회피.
     *
     * 호출자([com.quantjumpstock.core.infrastructure.migration.AppSecretReencryptionRunner])는
     * 이 ID 리스트로 [com.quantjumpstock.core.infrastructure.migration.AppSecretRowReencryptionService.reencryptOne]
     * 을 row 단위로 호출하여 REQUIRES_NEW 트랜잭션으로 처리한다.
     *
     * 휴지통 row (deleted_at IS NOT NULL) 도 backfill 대상 — 곧 hard delete 되지만 그 사이 보안 일관성 유지.
     */
    @Query("SELECT k.id FROM UserKisAccountEntity k WHERE k.appSecretEncryptedV2 IS NULL")
    fun findIdsForReencryption(): List<Long>
}
