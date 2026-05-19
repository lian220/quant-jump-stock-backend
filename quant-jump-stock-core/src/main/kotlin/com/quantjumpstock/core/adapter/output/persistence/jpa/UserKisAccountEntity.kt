package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp

/**
 * 사용자별 KIS 계정 정보
 * 민감한 정보(appSecret)는 암호화되어 저장됩니다.
 */
@Entity
@Table(name = "user_kis_accounts")
class UserKisAccountEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // user_id 의 unique 제약은 partial unique index 가 담당:
    //   V61 (A+):     (user_id)                WHERE deleted_at IS NULL
    //   V63 (1B):     (user_id, account_type)  WHERE deleted_at IS NULL
    // 따라서 JPA 레벨에서 unique=true 를 걸면 schema validate 모드와 충돌하므로 제거.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    @Column(name = "app_key", nullable = false, length = 100)
    val appKey: String,

    /**
     * 암호화된 App Secret (V1: AES/ECB - legacy)
     * Phase 1A PRE-요구사항 P3: V62 backfill 후 V63에서 제거 예정.
     */
    @Column(name = "app_secret_encrypted", nullable = false, length = 500)
    val appSecretEncrypted: String,

    /**
     * 암호화된 App Secret (V2: AES/GCM)
     * Base64(IV(12B) || ciphertext+tag(16B)) 포맷.
     * 부팅 시 [com.quantjumpstock.core.infrastructure.migration.AppSecretReencryptionRunner]가
     * 누락 row를 backfill (ECB → GCM 재암호화).
     */
    @Column(name = "app_secret_encrypted_v2", length = 1024)
    var appSecretEncryptedV2: String? = null,

    /**
     * 계좌번호 (앞 8자리)
     */
    @Column(name = "account_number", nullable = false, length = 20)
    val accountNumber: String,

    /**
     * 계좌 상품 코드 (01: 해외주식)
     */
    @Column(name = "account_product_code", nullable = false, length = 2)
    val accountProductCode: String = "01",

    /**
     * 계정 타입 (REAL: 실전, MOCK: 모의)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 10)
    val accountType: KisAccountType = KisAccountType.MOCK,

    /**
     * 활성화 여부
     */
    @Column(nullable = false)
    val enabled: Boolean = true,

    /**
     * 마지막 사용 시간
     */
    @Column(name = "last_used_at")
    var lastUsedAt: LocalDateTime? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    /**
     * Soft delete 휴지통 마커.
     * NULL = 활성 (partial unique 가 사용자당 1개 강제),
     * NOT NULL = 휴지통 row (7일 경과 시 Cloud Scheduler 가 hard delete).
     */
    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
) {
    /**
     * 복호화된 App Secret 반환
     * Service 레이어에서 암호화 유틸리티를 사용하여 복호화
     */
    fun getDecryptedAppSecret(encryptionService: (String) -> String): String {
        return encryptionService(appSecretEncrypted)
    }

    /**
     * 계좌번호 전체 반환 (계좌번호 + 상품코드)
     */
    fun getFullAccountNumber(): String {
        return accountNumber + accountProductCode
    }
}

enum class KisAccountType {
    REAL,   // 실전 투자
    MOCK    // 모의 투자
}
