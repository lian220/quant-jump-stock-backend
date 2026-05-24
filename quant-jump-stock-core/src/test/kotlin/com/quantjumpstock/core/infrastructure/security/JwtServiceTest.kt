package com.quantjumpstock.core.infrastructure.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.quantjumpstock.core.domain.port.output.TokenType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.util.Date

/**
 * JwtService 회귀 가드 + 신규 type claim 검증.
 *
 * Phase 1A 보안 PRE:
 * - copyOf(32) 제거 후에도 기존 access token 발급/검증 회귀 없음 확인
 * - access token 위치에 refresh token 거부 (Token Confusion 방어)
 * - refresh token 위치에 access token 거부
 * - type claim 없는 기존 토큰은 access 로 graceful fallback
 */
class JwtServiceTest {

    private val secret = "test-secret-with-at-least-32-bytes-for-hmac"
    private val issuer = "quant-jump-stock-test"
    private val devEnv = MockEnvironment().apply { setActiveProfiles("test") }

    private fun service(refreshDays: Long = 14) = JwtService(
        secret = secret,
        accessExpirationHours = 1,
        refreshExpirationDays = refreshDays,
        issuer = issuer,
        environment = devEnv,
    )

    @Test
    fun `access token 발급 후 validateAccessToken 으로 정상 검증된다`() {
        val sut = service()
        val token = sut.generateAccessToken("user-A", "a@example.com", "USER", dbId = 7L)

        val claims = sut.validateAccessToken(token)

        assertThat(claims).isNotNull
        assertThat(claims!!.userId).isEqualTo("user-A")
        assertThat(claims.email).isEqualTo("a@example.com")
        assertThat(claims.role).isEqualTo("USER")
        assertThat(claims.dbId).isEqualTo(7L)
        assertThat(claims.type).isEqualTo(TokenType.ACCESS)
    }

    @Test
    fun `refresh token 발급 후 validateRefreshToken 으로 정상 검증된다 + jti 보존`() {
        val sut = service()
        val jti = java.util.UUID.randomUUID().toString()
        val token = sut.generateRefreshToken("user-A", jti = jti, dbId = 7L)

        val claims = sut.validateRefreshToken(token)

        assertThat(claims).isNotNull
        assertThat(claims!!.userId).isEqualTo("user-A")
        assertThat(claims.dbId).isEqualTo(7L)
        assertThat(claims.type).isEqualTo(TokenType.REFRESH)
        assertThat(claims.jti).isEqualTo(jti)
    }

    @Test
    fun `Token Confusion 방어 - refresh token 을 access 자리에서 사용하면 거부`() {
        val sut = service()
        val refresh = sut.generateRefreshToken("user-A", jti = java.util.UUID.randomUUID().toString(), dbId = 7L)

        val asAccess = sut.validateAccessToken(refresh)

        assertThat(asAccess).isNull()
    }

    @Test
    fun `Token Confusion 방어 - access token 을 refresh 자리에서 사용하면 거부`() {
        val sut = service()
        val access = sut.generateAccessToken("user-A", "a@example.com", "USER", 7L)

        val asRefresh = sut.validateRefreshToken(access)

        assertThat(asRefresh).isNull()
    }

    @Test
    fun `graceful migration - type claim 없는 레거시 토큰은 access 로 통과`() {
        val sut = service()
        // type claim 없이 수동 발급 (배포 전 발급된 토큰 시뮬레이션)
        val legacyToken = SignedJWT(
            JWSHeader(JWSAlgorithm.HS256),
            JWTClaimsSet.Builder()
                .subject("legacy-user")
                .issuer(issuer)
                .claim("role", "USER")
                .claim("email", "legacy@example.com")
                .claim("dbId", 99L)
                .issueTime(Date())
                .expirationTime(Date(System.currentTimeMillis() + 3600_000))
                .build()
        ).apply { sign(MACSigner(secret.toByteArray())) }.serialize()

        val claims = sut.validateAccessToken(legacyToken)

        assertThat(claims).isNotNull
        assertThat(claims!!.userId).isEqualTo("legacy-user")
        assertThat(claims.dbId).isEqualTo(99L)
    }

    @Test
    fun `graceful migration - type claim 없는 레거시 토큰은 refresh 로는 거부`() {
        val sut = service()
        val legacyToken = SignedJWT(
            JWSHeader(JWSAlgorithm.HS256),
            JWTClaimsSet.Builder()
                .subject("legacy-user")
                .issuer(issuer)
                .issueTime(Date())
                .expirationTime(Date(System.currentTimeMillis() + 3600_000))
                .build()
        ).apply { sign(MACSigner(secret.toByteArray())) }.serialize()

        assertThat(sut.validateRefreshToken(legacyToken)).isNull()
    }

    @Test
    fun `만료된 토큰은 거부된다`() {
        val sut = service()
        val expired = SignedJWT(
            JWSHeader(JWSAlgorithm.HS256),
            JWTClaimsSet.Builder()
                .subject("user-A")
                .issuer(issuer)
                .claim("type", "access")
                .issueTime(Date(System.currentTimeMillis() - 2 * 3600_000))
                .expirationTime(Date(System.currentTimeMillis() - 3600_000))
                .build()
        ).apply { sign(MACSigner(secret.toByteArray())) }.serialize()

        assertThat(sut.validateAccessToken(expired)).isNull()
    }

    @Test
    fun `잘못된 서명 토큰은 거부된다`() {
        val sut = service()
        val foreign = SignedJWT(
            JWSHeader(JWSAlgorithm.HS256),
            JWTClaimsSet.Builder()
                .subject("attacker")
                .claim("type", "access")
                .expirationTime(Date(System.currentTimeMillis() + 3600_000))
                .build()
        ).apply { sign(MACSigner("different-secret-that-also-needs-32-bytes-or-more".toByteArray())) }.serialize()

        assertThat(sut.validateAccessToken(foreign)).isNull()
    }

    @Test
    fun `legacy generateToken 은 access token 으로 위임된다 - 하위 호환`() {
        val sut = service()
        @Suppress("DEPRECATION")
        val token = sut.generateToken("user-A", "a@example.com", "USER", 7L)

        // 신규 validateAccessToken 으로도 통과해야 함
        val newApi = sut.validateAccessToken(token)
        assertThat(newApi).isNotNull
        assertThat(newApi!!.type).isEqualTo(TokenType.ACCESS)

        // 레거시 validateToken 도 통과
        @Suppress("DEPRECATION")
        val legacy = sut.validateToken(token)
        assertThat(legacy).isNotNull
        assertThat(legacy!!.userId).isEqualTo("user-A")
        assertThat(legacy.dbId).isEqualTo(7L)
    }

    @Test
    fun `prod profile 에서 dev secret 사용 시 기동 차단`() {
        val prodEnv = MockEnvironment().apply { setActiveProfiles("prod") }

        val exception = runCatching {
            JwtService(
                secret = "quant-jump-stock-dev-secret-minimum-32-characters",
                accessExpirationHours = 1,
                refreshExpirationDays = 14,
                issuer = issuer,
                environment = prodEnv,
            )
        }.exceptionOrNull()

        assertThat(exception)
            .isNotNull
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(exception!!.message).contains("프로덕션")
    }

    @Test
    fun `32바이트 미만 secret 은 거부된다`() {
        val exception = runCatching {
            JwtService(
                secret = "too-short",
                accessExpirationHours = 1,
                refreshExpirationDays = 14,
                issuer = issuer,
                environment = devEnv,
            )
        }.exceptionOrNull()

        assertThat(exception)
            .isNotNull
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
