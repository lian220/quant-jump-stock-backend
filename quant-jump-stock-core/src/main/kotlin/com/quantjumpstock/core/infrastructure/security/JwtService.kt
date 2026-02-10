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
    private val signer: JWSSigner = MACSigner(secret.toByteArray().copyOf(32))
    private val verifier: JWSVerifier = MACVerifier(secret.toByteArray().copyOf(32))

    fun generateToken(userId: String, email: String?, role: String): String {
        val now = Date()
        val expiration = Date(now.time + expirationHours * 3600 * 1000)

        val claimsSet = JWTClaimsSet.Builder()
            .subject(userId)
            .issuer(issuer)
            .claim("role", role)
            .claim("email", email ?: "")
            .issueTime(now)
            .expirationTime(expiration)
            .build()

        val signedJWT = SignedJWT(
            JWSHeader(JWSAlgorithm.HS256),
            claimsSet
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

            if (claims.expirationTime.before(Date())) {
                logger.debug("JWT 만료됨")
                return null
            }

            JwtClaims(
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
    val userId: String,
    val email: String?,
    val role: String
)
