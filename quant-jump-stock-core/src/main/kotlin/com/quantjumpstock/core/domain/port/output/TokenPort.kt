package com.quantjumpstock.core.domain.port.output

/**
 * 토큰 발급/검증 포트.
 *
 * Hexagonal Architecture: Application 레이어가 토큰 메커니즘(JWT, PASETO 등)에 직접 의존하지 않도록 추상화.
 * 구현체는 infrastructure/security 레이어에 위치.
 *
 * 토큰 타입 분리 (RFC 9700, OWASP A07 2025):
 * - ACCESS: 단기 (15분~24시간) — API 인증
 * - REFRESH: 장기 (~14일) — access token 재발급용, httpOnly cookie로만 전송
 *
 * validateAccessToken은 REFRESH 타입을 거부하고, validateRefreshToken은 ACCESS 타입을 거부한다.
 * 토큰 혼용(Token Confusion) 공격 방어 (CWE-287).
 */
interface TokenPort {
    fun generateAccessToken(userId: String, email: String?, role: String, dbId: Long? = null): String

    /**
     * refresh token 발급. jti(JWT ID, UUID) 를 claim 에 포함시켜 서버 측 revocation 을 가능하게 한다.
     * 호출자는 jti 를 함께 DB(refresh_tokens) 에 저장해야 한다.
     */
    fun generateRefreshToken(userId: String, jti: String, dbId: Long? = null): String

    fun validateAccessToken(token: String): TokenClaims?

    /**
     * refresh token 검증. type=refresh + 서명/만료 검증을 통과한 경우 TokenClaims 반환.
     * 반환된 jti 로 호출자가 DB 의 revoke 상태를 확인해야 한다.
     */
    fun validateRefreshToken(token: String): TokenClaims?

    /**
     * 만료를 무시하고 서명만 검증해 sub/dbId 를 추출 — logout 처럼 만료 직후 사용자
     * 의지로 세션을 정리해야 하는 경로 전용.
     *
     * 보안: 만료 무시는 logout 외 절대 사용 금지. type 필드는 검증하지 않으므로
     * access/refresh 두 종류 모두 통과한다. logout 이후 추가 인가에는 사용하지 말 것.
     *
     * @return 서명 유효 시 (userId, dbId), 서명 불일치/파싱 실패 시 null
     */
    fun extractSubjectIgnoreExpiry(token: String): SubjectInfo?
}

data class TokenClaims(
    val dbId: Long?,
    val userId: String,
    val email: String?,
    val role: String,
    val type: TokenType,
    val jti: String? = null,
)

data class SubjectInfo(
    val userId: String,
    val dbId: Long?,
)

enum class TokenType { ACCESS, REFRESH }
