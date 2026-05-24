package com.quantjumpstock.core.adapter.input.rest.auth

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.auth.LoginRequest
import com.quantjumpstock.core.application.auth.LoginResponse
import com.quantjumpstock.core.application.auth.OAuthCodeExchangeService
import com.quantjumpstock.core.application.auth.RefreshTokenService
import com.quantjumpstock.core.application.auth.SignupRequest
import com.quantjumpstock.core.application.auth.SignupResponse
import com.quantjumpstock.core.config.JwtCookieProperties
import com.quantjumpstock.core.domain.port.output.TokenPort
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

/**
 * 인증 Controller
 *
 * Phase 1A 보안 PRE Task 12:
 * - Login/Signup/OAuth 응답에 refresh httpOnly cookie 발급
 * - Logout 응답에 cookie 만료 (Max-Age=0) + server-side revoke
 * - POST /auth/refresh 신규: refresh cookie → 새 access token + (옵션) refresh rotation
 *
 * OAuth2 로그인 흐름 (Frontend-first):
 * - FE → Naver OAuth URL로 직접 리다이렉트
 * - Naver → FE /auth/callback/{provider}?code=... 로 리다이렉트
 * - FE → POST /api/v1/auth/oauth2/code/{provider} {code, redirectUri} → JWT 반환
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "인증 API")
class AuthController(
    private val authService: AuthService,
    private val oauthCodeExchangeService: OAuthCodeExchangeService,
    private val refreshTokenService: RefreshTokenService,
    private val tokenPort: TokenPort,
    private val cookieProps: JwtCookieProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "사용자 ID와 비밀번호로 로그인")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val response = authService.login(request)
        return responseWithRefreshCookie(response, response.refreshToken)
    }

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "새 사용자 계정 생성")
    fun signup(@RequestBody request: SignupRequest): ResponseEntity<SignupResponse> {
        val response = authService.signup(request)
        return if (response.success) {
            responseWithRefreshCookie(response, response.refreshToken)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }

    @GetMapping("/me")
    @Operation(summary = "현재 사용자 정보", description = "로그인된 사용자 정보 조회")
    fun getCurrentUser(@RequestHeader("Authorization") token: String?): ResponseEntity<LoginResponse> {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build()
        }
        val actualToken = token.removePrefix("Bearer ")
        val response = authService.validateToken(actualToken)
        return if (response != null) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(401).build()
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "세션 종료 + refresh token 서버 측 revoke + cookie 만료")
    fun logout(
        @RequestHeader(value = "Authorization", required = false) token: String?,
    ): ResponseEntity<Map<String, Any>> {
        val revokedCount = authService.logout(token)
        logger.info("logout — revoked refresh tokens: {}", revokedCount)
        val expiredCookie = buildExpiredRefreshCookie()
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
            .body(mapOf(
                "success" to true,
                "message" to "Logged out successfully"
            ))
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Access token 재발급",
        description = "httpOnly refresh cookie 로 새 access token 발급. RFC 9700 준수."
    )
    fun refresh(
        @CookieValue(value = "refresh_token", required = false) refreshTokenCookie: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> {
        // SameSite=Lax 단독 보호의 bypass 위험에 대비한 임시 CSRF 방어 — CSRF 토큰(Task 12-B) 도입 시 제거
        if (!isAllowedOrigin(request)) {
            logger.warn("/auth/refresh 거부 — 허용되지 않은 Origin/Referer")
            return ResponseEntity.status(403).body(mapOf("success" to false, "message" to "Forbidden"))
        }
        val refreshToken = refreshTokenCookie ?: return unauthorizedRefresh("refresh token 이 없습니다")
        val validated = refreshTokenService.validate(refreshToken)
            ?: return unauthorizedRefresh("refresh token 이 유효하지 않습니다")

        // role/email 은 access token 재발급 시 db 조회 없이 기존 토큰 발급 흐름을 재사용하기 위해
        // refresh 검증 결과 그대로 신뢰 — 추후 role 변경 즉시 반영이 필요하면 user 재조회 추가.
        val accessToken = tokenPort.generateAccessToken(
            userId = validated.userId,
            email = null,
            role = "USER",
            dbId = validated.userDbId,
        )
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("Pragma", "no-cache")
            .body(mapOf<String, Any>(
                "success" to true,
                "accessToken" to accessToken,
            ))
    }

    @GetMapping("/oauth2/urls")
    @Operation(summary = "OAuth2 로그인 URL 목록", description = "프론트엔드에서 사용할 OAuth2 로그인 URL 제공")
    fun getOAuth2Urls(): ResponseEntity<Map<String, String>> {
        val baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
        return ResponseEntity.ok(mapOf(
            "naver" to "$baseUrl/api/v1/auth/oauth2/authorize/naver"
        ))
    }

    @PostMapping("/oauth2/code/{provider}")
    @Operation(
        summary = "OAuth2 코드 교환",
        description = "Frontend에서 받은 Authorization Code를 JWT로 교환. " +
            "현재 지원: naver / 추후 google, kakao 확장 예정"
    )
    fun exchangeOAuthCode(
        @PathVariable provider: String,
        @RequestBody request: OAuthCodeExchangeService.OAuthCodeRequest
    ): ResponseEntity<OAuthCodeExchangeService.OAuthTokenResponse> {
        val response = oauthCodeExchangeService.exchange(provider, request)
        return responseWithRefreshCookie(response, response.refreshToken)
    }

    // -------------------------------------------------------------------------
    // Private helpers — Cookie 발급 책임을 Controller 안에 캡슐화
    // -------------------------------------------------------------------------

    private fun <T> responseWithRefreshCookie(body: T, refreshToken: String?): ResponseEntity<T> {
        val builder = ResponseEntity.ok().cacheControl(CacheControl.noStore())
        if (refreshToken != null) {
            builder.header(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshToken).toString())
        }
        return builder.body(body)
    }

    private fun buildRefreshCookie(token: String): ResponseCookie {
        val builder = ResponseCookie.from(cookieProps.name, token)
            .httpOnly(true)
            .secure(cookieProps.secure)
            .sameSite(cookieProps.sameSite)
            .path(cookieProps.path)
            .maxAge(cookieProps.maxAgeDays * 24 * 3600)
        if (cookieProps.domain.isNotBlank()) {
            builder.domain(cookieProps.domain)
        }
        return builder.build()
    }

    private fun buildExpiredRefreshCookie(): ResponseCookie {
        val builder = ResponseCookie.from(cookieProps.name, "")
            .httpOnly(true)
            .secure(cookieProps.secure)
            .sameSite(cookieProps.sameSite)
            .path(cookieProps.path)
            .maxAge(0)
        if (cookieProps.domain.isNotBlank()) {
            builder.domain(cookieProps.domain)
        }
        return builder.build()
    }

    private fun unauthorizedRefresh(message: String): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(401)
            .cacheControl(CacheControl.noStore())
            .body(mapOf("success" to false, "message" to message))

    /**
     * Origin/Referer 헤더 검증 (CSRF Task 12-B 활성화 전 임시 방어).
     *
     * - 허용: 같은 출처(Origin=현재 서버) 또는 jwt.cookie.domain 으로 끝나는 호스트
     * - 거부: Origin/Referer 모두 없는 경우 (브라우저 fetch 는 항상 Origin 첨부)
     * - 거부: 외부 도메인
     */
    private fun isAllowedOrigin(request: HttpServletRequest): Boolean {
        val origin = request.getHeader("Origin") ?: request.getHeader("Referer") ?: return false
        return runCatching {
            val originHost = java.net.URI(origin).host ?: return false
            val cookieDomain = cookieProps.domain.trimStart('.')
            originHost == "localhost"
                || originHost == "127.0.0.1"
                || originHost == request.serverName
                || (cookieDomain.isNotBlank() &&
                    (originHost == cookieDomain || originHost.endsWith(".$cookieDomain")))
        }.getOrDefault(false)
    }
}
