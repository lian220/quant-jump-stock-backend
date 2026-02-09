package com.quantjumpstock.core.adapter.input.rest.auth

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.auth.LoginRequest
import com.quantjumpstock.core.application.auth.LoginResponse
import com.quantjumpstock.core.application.auth.OAuthService
import com.quantjumpstock.core.application.auth.SignupRequest
import com.quantjumpstock.core.application.auth.SignupResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 인증 Controller
 * 관리자/사용자 로그인 처리
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "인증 API")
class AuthController(
    private val authService: AuthService,
    private val oAuthService: OAuthService
) {

    /**
     * 로그인
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    @Operation(summary = "로그인", description = "사용자 ID와 비밀번호로 로그인")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }

    /**
     * 회원가입
     * POST /api/v1/auth/signup
     */
    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "새 사용자 계정 생성")
    fun signup(@RequestBody request: SignupRequest): ResponseEntity<SignupResponse> {
        val response = authService.signup(request)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }

    /**
     * 현재 사용자 정보 조회
     * GET /api/v1/auth/me
     */
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

    /**
     * 로그아웃
     * POST /api/v1/auth/logout
     */
    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "세션 종료")
    fun logout(@RequestHeader("Authorization") token: String?): ResponseEntity<Map<String, Any>> {
        if (token != null && token.startsWith("Bearer ")) {
            val actualToken = token.removePrefix("Bearer ")
            authService.logout(actualToken)
        }

        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "Logged out successfully"
        ))
    }

    // ==================== OAuth 엔드포인트 ====================

    /**
     * Google OAuth 로그인 시작
     * GET /api/v1/auth/google
     */
    @GetMapping("/google")
    @Operation(summary = "Google OAuth 로그인", description = "Google OAuth 인증 페이지로 리다이렉트")
    fun googleLogin(
        @RequestParam("redirect_uri", required = false) redirectUri: String?,
        response: HttpServletResponse
    ) {
        val authUrl = oAuthService.getGoogleAuthUrl(redirectUri)
        response.sendRedirect(authUrl)
    }

    /**
     * Google OAuth 콜백
     * GET /api/v1/auth/google/callback
     */
    @GetMapping("/google/callback")
    @Operation(summary = "Google OAuth 콜백", description = "Google OAuth 인증 완료 후 콜백")
    fun googleCallback(
        @RequestParam("code") code: String,
        @RequestParam("state", required = false) state: String?,
        response: HttpServletResponse
    ) {
        val result = oAuthService.handleGoogleCallback(code, state)
        response.sendRedirect(result.redirectUrl)
    }

    /**
     * Naver OAuth 로그인 시작
     * GET /api/v1/auth/naver
     */
    @GetMapping("/naver")
    @Operation(summary = "Naver OAuth 로그인", description = "Naver OAuth 인증 페이지로 리다이렉트")
    fun naverLogin(
        @RequestParam("redirect_uri", required = false) redirectUri: String?,
        response: HttpServletResponse
    ) {
        val authUrl = oAuthService.getNaverAuthUrl(redirectUri)
        response.sendRedirect(authUrl)
    }

    /**
     * Naver OAuth 콜백
     * GET /api/v1/auth/naver/callback
     */
    @GetMapping("/naver/callback")
    @Operation(summary = "Naver OAuth 콜백", description = "Naver OAuth 인증 완료 후 콜백")
    fun naverCallback(
        @RequestParam("code") code: String,
        @RequestParam("state", required = false) state: String?,
        response: HttpServletResponse
    ) {
        val result = oAuthService.handleNaverCallback(code, state)
        response.sendRedirect(result.redirectUrl)
    }

    /**
     * Naver OAuth 토큰 교환 (Client-side OAuth)
     * POST /api/v1/auth/naver/token
     */
    @PostMapping("/naver/token")
    @Operation(summary = "Naver OAuth 토큰 교환", description = "프론트엔드에서 받은 code로 토큰 교환")
    fun naverTokenExchange(@RequestBody request: Map<String, String>): ResponseEntity<LoginResponse> {
        val code = request["code"] ?: return ResponseEntity.badRequest().body(
            LoginResponse(
                success = false,
                error = "code parameter is required"
            )
        )

        return try {
            val result = oAuthService.exchangeNaverCode(code)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            ResponseEntity.status(500).body(
                LoginResponse(
                    success = false,
                    error = e.message ?: "Token exchange failed"
                )
            )
        }
    }
}
