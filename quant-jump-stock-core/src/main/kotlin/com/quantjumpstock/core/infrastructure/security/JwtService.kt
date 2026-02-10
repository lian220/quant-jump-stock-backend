package com.quantjumpstock.core.infrastructure.security

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date

@Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration-hours}") private val expirationHours: Long,
    @Value("\${jwt.issuer}") private val issuer: String
) {
    private val logger = LoggerFactory.getLogger(JwtService::class.java)

    init {
        require(secret.toByteArray().size >= 32) {
            "JWT secret must be at least 32 bytes, got ${secret.toByteArray().size}"
        }
        if (secret.startsWith("quant-jump-stock-dev-secret")) {
            logger.warn("개발용 기본 JWT secret 사용 중 - 프로덕션 환경에서는 반드시 JWT_SECRET 환경변수를 설정하세요")
        }
    }

    private val secretBytes = secret.toByteArray().copyOf(32)
    private val signer: JWSSigner = MACSigner(secretBytes)
    private val verifier: JWSVerifier = MACVerifier(secretBytes)

    fun generateToken(userId: String, email: String?, role: String, dbId: Long? = null): String {
        val now = Date()
        val expiration = Date(now.time + expirationHours * 3600 * 1000)

        val builder = JWTClaimsSet.Builder()
            .subject(userId)
            .issuer(issuer)
            .claim("role", role)
            .claim("email", email ?: "")
            .issueTime(now)
            .expirationTime(expiration)

        if (dbId != null) {
            builder.claim("dbId", dbId)
        }

        val signedJWT = SignedJWT(
            JWSHeader(JWSAlgorithm.HS256),
            builder.build()
        )
        signedJWT.sign(signer)

        return signedJWT.serialize()
    }

    fun validateToken(token: String): JwtClaims? {
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

            JwtClaims(
                dbId = claims.getLongClaim("dbId"),
                userId = claims.subject,
                email = claims.getStringClaim("email"),
                role = claims.getStringClaim("role") ?: "USER"
            )
        } catch (e: Exception) {
            logger.warn("JWT 파싱 실패: ${e.message}")
            null
        }
    }
}

data class JwtClaims(
    val dbId: Long?,
    val userId: String,
    val email: String?,
    val role: String
)
