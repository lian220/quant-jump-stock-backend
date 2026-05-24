package com.quantjumpstock.core.infrastructure.security

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.quantjumpstock.core.domain.port.output.TokenClaims
import com.quantjumpstock.core.domain.port.output.TokenPort
import com.quantjumpstock.core.domain.port.output.TokenType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.util.Date

/**
 * JWT 발급/검증 어댑터.
 *
 * 보안 결정 (Phase 1A 보안 PRE):
 * - copyOf(32) 제거: HMAC-SHA256은 키 길이 제한이 없으므로 secret 원본 바이트 사용 (엔트로피 보존)
 * - type claim (access|refresh) 도입: 토큰 혼용 공격 방어 (RFC 9700)
 * - graceful migration: 기존 토큰(type claim 없음)은 ACCESS로 간주 (배포 후 ~24h 안에 자연 만료)
 * - dev secret 사용 시 prod profile에서는 기동 차단
 */
@Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration-hours}") private val accessExpirationHours: Long,
    @Value("\${jwt.refresh-expiration-days:14}") private val refreshExpirationDays: Long,
    @Value("\${jwt.issuer}") private val issuer: String,
    environment: Environment,
) : TokenPort {
    private val logger = LoggerFactory.getLogger(JwtService::class.java)

    init {
        require(secret.toByteArray().size >= 32) {
            "JWT secret must be at least 32 bytes, got ${secret.toByteArray().size}"
        }
        if (secret.startsWith("quant-jump-stock-dev-secret")) {
            val isProd = environment.activeProfiles.any { it.equals("prod", ignoreCase = true) }
            check(!isProd) {
                "프로덕션 환경에서 개발용 JWT secret 사용 불가. JWT_SECRET 환경변수를 설정하세요."
            }
            logger.warn("개발용 기본 JWT secret 사용 중 - 프로덕션 환경에서는 반드시 JWT_SECRET 환경변수를 설정하세요")
        }
    }

    private val secretBytes: ByteArray = secret.toByteArray()
    private val signer: JWSSigner = MACSigner(secretBytes)
    private val verifier: JWSVerifier = MACVerifier(secretBytes)

    override fun generateAccessToken(userId: String, email: String?, role: String, dbId: Long?): String {
        val now = Date()
        val expiration = Date(now.time + accessExpirationHours * 3600 * 1000)

        val builder = JWTClaimsSet.Builder()
            .subject(userId)
            .issuer(issuer)
            .claim("type", TYPE_ACCESS)
            .claim("role", role)
            .claim("email", email ?: "")
            .issueTime(now)
            .expirationTime(expiration)

        if (dbId != null) {
            builder.claim("dbId", dbId)
        }

        return sign(builder.build())
    }

    override fun generateRefreshToken(userId: String, jti: String, dbId: Long?): String {
        val now = Date()
        val expiration = Date(now.time + refreshExpirationDays * 24 * 3600 * 1000)

        val builder = JWTClaimsSet.Builder()
            .subject(userId)
            .issuer(issuer)
            .jwtID(jti)
            .claim("type", TYPE_REFRESH)
            .issueTime(now)
            .expirationTime(expiration)

        if (dbId != null) {
            builder.claim("dbId", dbId)
        }

        return sign(builder.build())
    }

    override fun validateAccessToken(token: String): TokenClaims? {
        val claims = parseAndVerify(token) ?: return null
        val type = claims.getStringClaim("type")
        // graceful migration: type claim이 없는 기존 발급 토큰은 access로 간주
        if (type != null && type != TYPE_ACCESS) {
            logger.warn("access token 위치에 type={} 토큰 거부", type)
            return null
        }
        return toClaims(claims, TokenType.ACCESS)
    }

    override fun validateRefreshToken(token: String): TokenClaims? {
        val claims = parseAndVerify(token) ?: return null
        val type = claims.getStringClaim("type")
        if (type != TYPE_REFRESH) {
            logger.warn("refresh token 위치에 type={} 토큰 거부", type ?: "null")
            return null
        }
        return toClaims(claims, TokenType.REFRESH)
    }

    /**
     * 레거시 호환: 기존 generateToken 호출은 access token 발급으로 위임.
     * 새 코드는 generateAccessToken / generateRefreshToken 사용.
     */
    @Deprecated(
        "Use generateAccessToken or generateRefreshToken",
        ReplaceWith("generateAccessToken(userId, email, role, dbId)")
    )
    fun generateToken(userId: String, email: String?, role: String, dbId: Long? = null): String =
        generateAccessToken(userId, email, role, dbId)

    /**
     * 레거시 호환: 기존 validateToken 호출은 access token 검증으로 위임 + JwtClaims로 매핑.
     */
    @Deprecated(
        "Use validateAccessToken",
        ReplaceWith("validateAccessToken(token)")
    )
    fun validateToken(token: String): JwtClaims? =
        validateAccessToken(token)?.let {
            JwtClaims(dbId = it.dbId, userId = it.userId, email = it.email, role = it.role)
        }

    private fun sign(claims: JWTClaimsSet): String {
        val signedJWT = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        signedJWT.sign(signer)
        return signedJWT.serialize()
    }

    private fun parseAndVerify(token: String): JWTClaimsSet? {
        return try {
            val signedJWT = SignedJWT.parse(token)
            if (!signedJWT.verify(verifier)) {
                logger.warn("JWT 서명 검증 실패")
                return null
            }
            val claims = signedJWT.jwtClaimsSet
            val expirationTime = claims.expirationTime
            if (expirationTime == null || expirationTime.before(Date())) {
                logger.debug("JWT 만료됨")
                return null
            }
            claims
        } catch (e: Exception) {
            logger.warn("JWT 파싱 실패: ${e.message}")
            null
        }
    }

    private fun toClaims(claims: JWTClaimsSet, type: TokenType): TokenClaims =
        TokenClaims(
            dbId = claims.getLongClaim("dbId"),
            userId = claims.subject,
            email = claims.getStringClaim("email"),
            role = claims.getStringClaim("role") ?: "USER",
            type = type,
            jti = claims.jwtid,
        )

    companion object {
        private const val TYPE_ACCESS = "access"
        private const val TYPE_REFRESH = "refresh"
    }
}

/**
 * @deprecated Use [TokenClaims] instead. Kept for backwards compatibility with existing callers.
 */
@Deprecated("Use TokenClaims from domain/port/output/TokenPort.kt")
data class JwtClaims(
    val dbId: Long?,
    val userId: String,
    val email: String?,
    val role: String
)
