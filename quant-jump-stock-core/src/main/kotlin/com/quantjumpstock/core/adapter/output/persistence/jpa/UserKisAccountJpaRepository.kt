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

    /**
     * V2(GCM) 컬럼이 비어 있는 row 전체 조회.
     * Phase 1A PRE-P3: 부팅 시 ECB → GCM 재암호화 backfill 대상.
     *
     * @deprecated 메모리 압박 위험 (entity 전체 + LAZY user 연관 로드).
     *             대신 [findIdsForReencryption] 으로 ID 만 조회 후 row 단위 처리.
     */
    @Deprecated("Use findIdsForReencryption() — entity full load is unsafe at scale")
    fun findAllByAppSecretEncryptedV2IsNull(): List<UserKisAccountEntity>

    /**
     * V2(GCM) backfill 대상 row 의 PK 만 조회. entity full load 회피.
     *
     * 호출자([com.quantjumpstock.core.infrastructure.migration.AppSecretReencryptionRunner])는
     * 이 ID 리스트로 [com.quantjumpstock.core.infrastructure.migration.AppSecretRowReencryptionService.reencryptOne]
     * 을 row 단위로 호출하여 REQUIRES_NEW 트랜잭션으로 처리한다.
     */
    @Query("SELECT k.id FROM UserKisAccountEntity k WHERE k.appSecretEncryptedV2 IS NULL")
    fun findIdsForReencryption(): List<Long>
}
