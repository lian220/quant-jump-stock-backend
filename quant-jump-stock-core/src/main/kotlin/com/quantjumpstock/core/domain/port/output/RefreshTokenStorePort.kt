package com.quantjumpstock.core.domain.port.output

import java.time.Instant
import java.util.UUID

/**
 * Refresh token 서버 측 저장/조회 포트.
 *
 * Phase 1A 보안 PRE Task 12:
 * - logout 시 즉시 revoke 가능하도록 stateless JWT 한계를 보완 (RFC 9700)
 * - 추후 reuse detection / token family revocation 의 데이터 기반
 *
 * 구현체는 adapter/output/persistence/jpa 에 위치.
 */
interface RefreshTokenStorePort {
    /**
     * 새 refresh token 발급 시 호출. jti 를 PK 로 저장한다.
     */
    fun save(jti: UUID, userDbId: Long, issuedAt: Instant, expiresAt: Instant)

    /**
     * 검증 시 호출. jti 가 존재하고 revoke 되지 않았으며 만료되지 않은 경우 true.
     */
    fun isActive(jti: UUID, now: Instant = Instant.now()): Boolean

    /**
     * 사용자의 모든 refresh token 을 revoke (logout 등).
     * 반환값: revoke 된 row 수.
     */
    fun revokeAllByUser(userDbId: Long, now: Instant = Instant.now()): Int

    /**
     * 특정 jti 만 revoke (선택적 — 단일 세션 종료).
     */
    fun revokeByJti(jti: UUID, now: Instant = Instant.now())
}
