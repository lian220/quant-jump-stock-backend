package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp

/**
 * UserBrokerAccount JPA entity (Phase 1B v2.1).
 *
 * partial unique index `(user_id, broker, account_type, account_number) WHERE deleted_at IS NULL`
 * 가 활성 row 4-tuple 단위 유일성을 강제 (V63 마이그). JPA 레벨에서는 unique=true 안 걸음.
 */
@Entity
@Table(name = "user_broker_accounts")
class UserBrokerAccountEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    @Enumerated(EnumType.STRING)
    @Column(name = "broker", nullable = false, length = 20)
    val broker: BrokerEntityEnum,

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 10)
    val accountType: AccountTypeEntityEnum,

    @Column(name = "account_number", nullable = false, length = 20)
    val accountNumber: String,

    /** broker-specific. KIS='01' (해외주식). 다른 broker 에선 NULL (V65 마이그). */
    @Column(name = "account_product_code", length = 2)
    val accountProductCode: String? = null,

    @Column(name = "account_alias", length = 50)
    var accountAlias: String? = null,

    @Column(name = "app_key", nullable = false, length = 100)
    val appKey: String,

    /**
     * GCM 암호화된 app_secret. Base64(IV(12B) || ciphertext+tag(16B)).
     * V1 (ECB) 컬럼은 신규 테이블에서 제거됨 — V62 backfill 완료 후 도입.
     */
    @Column(name = "app_secret_encrypted", nullable = false, length = 1024)
    val appSecretEncrypted: String,

    @Column(nullable = false)
    val enabled: Boolean = true,

    @Column(name = "last_used_at")
    var lastUsedAt: LocalDateTime? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null,
)

/**
 * JPA layer 의 broker enum. domain 의 `Broker` 와 1:1 매핑.
 * 같은 enum 을 domain/JPA 양쪽에 두는 이유: hexagonal architecture 룰
 * (domain → JPA 의존 금지). adapter 가 mapper 로 변환.
 */
enum class BrokerEntityEnum { KIS, TOSS }

/**
 * JPA layer 의 account_type enum. domain 의 `AccountType` 과 1:1 매핑.
 */
enum class AccountTypeEntityEnum { MOCK, REAL }
