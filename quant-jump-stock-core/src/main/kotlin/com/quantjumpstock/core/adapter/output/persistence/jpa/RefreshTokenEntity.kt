package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Refresh token revocation store (Flyway V61).
 *
 * jti(JWT ID, UUID v4) 를 PK 로 사용해 발급된 refresh token 과 1:1 매핑.
 * logout/explicit revoke 시 is_revoked=true 로 set.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity(
    @Id
    @Column(name = "jti", nullable = false, updatable = false)
    val jti: UUID,

    @Column(name = "user_db_id", nullable = false)
    val userDbId: Long,

    @Column(name = "issued_at", nullable = false)
    val issuedAt: Instant,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "is_revoked", nullable = false)
    var isRevoked: Boolean = false,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
)
