package com.quantjumpstock.core.application.auth

import com.quantjumpstock.core.domain.port.output.RefreshTokenStorePort
import com.quantjumpstock.core.domain.port.output.TokenPort
import com.quantjumpstock.core.domain.port.output.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Refresh token 발급/검증/취소 유스케이스.
 *
 * Phase 1A 보안 PRE Task 12:
 * - JWT 발급은 TokenPort, 서버 측 jti 관리는 RefreshTokenStorePort 위임
 * - logout 시 user 의 모든 refresh token revoke (RFC 9700 최소 구현)
 * - rotation 은 본 Task 범위 밖 (별도 Task 12-rotation 으로 분리)
 */
@Service
class RefreshTokenService(
    private val tokenPort: TokenPort,
    private val store: RefreshTokenStorePort,
    private val userRepository: UserRepository,
    @Value("\${jwt.refresh-expiration-days:14}") private val refreshExpirationDays: Long,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 새 refresh token 발급 + DB 등록.
     * @return 발급된 refresh token 문자열 (cookie 로 발급되어야 함)
     */
    fun issue(userId: String, userDbId: Long, now: Instant = Instant.now()): String {
        val jti = UUID.randomUUID()
        val token = tokenPort.generateRefreshToken(userId, jti = jti.toString(), dbId = userDbId)
        val expiresAt = now.plusSeconds(refreshExpirationDays * 24 * 3600)
        store.save(jti = jti, userDbId = userDbId, issuedAt = now, expiresAt = expiresAt)
        return token
    }

    /**
     * 클라이언트가 보낸 refresh token 을 검증한다.
     * 1) JWT 서명/만료/type=refresh 확인 (TokenPort)
     * 2) jti 가 DB 에 살아있는지 확인 (RefreshTokenStorePort)
     *
     * @return 검증 통과 시 사용자 식별 정보, 실패 시 null
     */
    fun validate(token: String, now: Instant = Instant.now()): ValidatedRefresh? {
        val claims = tokenPort.validateRefreshToken(token) ?: return null
        val jtiString = claims.jti ?: run {
            logger.warn("refresh token 에 jti claim 부재 — 거부")
            return null
        }
        val jti = runCatching { UUID.fromString(jtiString) }.getOrNull() ?: run {
            logger.warn("refresh token jti 가 UUID 형식이 아님: {} — 거부", jtiString)
            return null
        }
        if (!store.isActive(jti, now)) {
            logger.warn("refresh token jti={} 가 revoke 되었거나 만료 — 거부", jti)
            return null
        }
        val userDbId = claims.dbId ?: run {
            logger.warn("refresh token 에 dbId claim 부재 — 거부")
            return null
        }

        // role/email 은 refresh 시점의 최신 DB 값을 사용 (CWE-863 권한 변경 즉시 반영).
        // refresh token 에 role/email 을 embed 하면 권한 강등/계정 비활성화가 14일간 미반영.
        val user = userRepository.findByUserId(claims.userId) ?: run {
            logger.warn("refresh token userId={} 해당 사용자 없음 — 거부", claims.userId)
            return null
        }
        return ValidatedRefresh(
            userId = claims.userId,
            userDbId = userDbId,
            jti = jti,
            role = user.role.name,
            email = user.email,
        )
    }

    /**
     * 사용자의 모든 refresh token 을 revoke (logout 등).
     */
    fun revokeAll(userDbId: Long, now: Instant = Instant.now()): Int =
        store.revokeAllByUser(userDbId, now)

    data class ValidatedRefresh(
        val userId: String,
        val userDbId: Long,
        val jti: UUID,
        val role: String,
        val email: String?,
    )
}
