package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.quantjumpstock.core.domain.port.output.RefreshTokenStorePort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * RefreshTokenStorePort JPA 어댑터.
 *
 * Hexagonal Architecture: application/auth 의 RefreshTokenService 가
 * RefreshTokenStorePort 인터페이스만 의존하고, 실제 PostgreSQL 영속성은
 * 이 어댑터가 담당.
 */
@Component
class RefreshTokenStoreAdapter(
    private val repository: RefreshTokenJpaRepository,
) : RefreshTokenStorePort {

    @Transactional
    override fun save(jti: UUID, userDbId: Long, issuedAt: Instant, expiresAt: Instant) {
        repository.save(
            RefreshTokenEntity(
                jti = jti,
                userDbId = userDbId,
                issuedAt = issuedAt,
                expiresAt = expiresAt,
            )
        )
    }

    override fun isActive(jti: UUID, now: Instant): Boolean {
        val entity = repository.findById(jti).orElse(null) ?: return false
        return !entity.isRevoked && entity.expiresAt.isAfter(now)
    }

    @Transactional
    override fun revokeAllByUser(userDbId: Long, now: Instant): Int =
        repository.revokeAllByUser(userDbId, now)

    @Transactional
    override fun revokeByJti(jti: UUID, now: Instant) {
        repository.revokeByJti(jti, now)
    }
}
