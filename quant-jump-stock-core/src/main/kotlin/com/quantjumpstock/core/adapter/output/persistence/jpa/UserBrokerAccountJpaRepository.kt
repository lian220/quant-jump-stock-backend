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

    /**
     * Phase 1D — legacy KIS 호출 경로용 (BalanceService / KisApiAdapter).
     * 사용자의 login id (String) 로 활성 KIS 계좌 단건 lookup.
     * 다중 KIS 계좌 보유 시 첫 번째 (createdAt 오름차순). 정확한 전략→계좌 매핑은
     * `strategy_subscriptions.broker_account_id` (Phase 1B) 가 따로 담당.
     */
    @Query(
        "SELECT a FROM UserBrokerAccountEntity a " +
            "JOIN a.user u " +
            "WHERE u.userId = :loginUserId " +
            "AND a.broker = com.quantjumpstock.core.adapter.output.persistence.jpa.BrokerEntityEnum.KIS " +
            "AND a.deletedAt IS NULL " +
            "AND a.enabled = true " +
            "ORDER BY a.createdAt ASC " +
            "LIMIT 1"
    )
    fun findFirstActiveKisByUserLoginId(@Param("loginUserId") loginUserId: String): Optional<UserBrokerAccountEntity>
}
