package com.quantjumpstock.core.adapter.output.persistence.jpa

import java.time.LocalDateTime
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserBrokerAccountJpaRepository : JpaRepository<UserBrokerAccountEntity, Long> {

    /** 사용자의 활성 row 전부 (deletedAt IS NULL). */
    @Query(
        "SELECT a FROM UserBrokerAccountEntity a " +
            "WHERE a.user.id = :userId AND a.deletedAt IS NULL"
    )
    fun findAllActiveByUserId(@Param("userId") userId: Long): List<UserBrokerAccountEntity>

    /** 4-tuple 로 활성 row 단건. */
    @Query(
        "SELECT a FROM UserBrokerAccountEntity a " +
            "WHERE a.user.id = :userId " +
            "AND a.broker = :broker " +
            "AND a.accountType = :accountType " +
            "AND a.accountNumber = :accountNumber " +
            "AND a.deletedAt IS NULL"
    )
    fun findActiveByUserIdAndKey(
        @Param("userId") userId: Long,
        @Param("broker") broker: BrokerEntityEnum,
        @Param("accountType") accountType: AccountTypeEntityEnum,
        @Param("accountNumber") accountNumber: String,
    ): Optional<UserBrokerAccountEntity>

    /** 사용자의 휴지통 row 전부 (deletedAt IS NOT NULL, 최근 순). */
    @Query(
        "SELECT a FROM UserBrokerAccountEntity a " +
            "WHERE a.user.id = :userId AND a.deletedAt IS NOT NULL ORDER BY a.deletedAt DESC"
    )
    fun findAllTrashedByUserId(@Param("userId") userId: Long): List<UserBrokerAccountEntity>

    /** Cloud Scheduler 가 호출. deleted_at < thresholdAt 인 모든 휴지통 row. */
    @Query(
        "SELECT a FROM UserBrokerAccountEntity a " +
            "WHERE a.deletedAt IS NOT NULL AND a.deletedAt < :thresholdAt"
    )
    fun findExpiredTrashed(@Param("thresholdAt") thresholdAt: LocalDateTime): List<UserBrokerAccountEntity>
}
