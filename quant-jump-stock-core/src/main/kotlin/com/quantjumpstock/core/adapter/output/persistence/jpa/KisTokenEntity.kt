package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp

/**
 * KIS API Access Token Entity
 * 사용자별, 계정 타입별로 KIS API Access Token을 저장합니다.
 *
 * MongoDB access_tokens 컬렉션에서 PostgreSQL로 마이그레이션
 */
@Entity
@Table(
    name = "kis_tokens",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_user_account_type",
            columnNames = ["user_id", "account_type"]
        )
    ],
    indexes = [
        Index(name = "idx_kis_tokens_user_account", columnList = "user_id, account_type"),
        Index(name = "idx_kis_tokens_expiration", columnList = "expiration_time")
    ]
)
class KisTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 10)
    val accountType: KisAccountType,

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    var accessToken: String,

    @Column(name = "expiration_time", nullable = false)
    var expirationTime: LocalDateTime,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 토큰이 만료되었는지 확인
     */
    fun isExpired(): Boolean {
        return LocalDateTime.now().isAfter(expirationTime)
    }

    /**
     * 토큰이 유효한지 확인 (활성화 + 만료 안 됨)
     */
    fun isValid(): Boolean {
        return isActive && !isExpired()
    }
}
