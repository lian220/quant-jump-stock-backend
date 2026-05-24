package com.quantjumpstock.core.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Refresh token httpOnly cookie 속성.
 *
 * application.yml 기본값(local dev): secure=false, sameSite=Lax, domain="" (localhost)
 * application-prod.yml override: secure=true, domain=.alphafoundry.app
 *
 * Phase 1A 보안 PRE Task 12:
 * - SameSite=Lax 채택 (Strict 는 cross-subdomain fetch 차단)
 * - domain 빈 문자열이면 host-only cookie (localhost dev 호환)
 */
@ConfigurationProperties(prefix = "jwt.cookie")
data class JwtCookieProperties(
    val name: String = "refresh_token",
    val secure: Boolean = false,
    val sameSite: String = "Lax",
    val domain: String = "",
    val path: String = "/",
    val maxAgeDays: Long = 14,
)
