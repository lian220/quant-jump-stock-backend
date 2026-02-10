package com.quantjumpstock.core.adapter.input.rest.auth

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.auth.LoginRequest
import com.quantjumpstock.core.application.auth.LoginResponse
import com.quantjumpstock.core.application.auth.SignupRequest
import com.quantjumpstock.core.application.auth.SignupResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 인증 Controller
 * 로그인/회원가입/사용자 정보 조회
 *
 * OAuth2 로그인은 Spring Security OAuth2 Client가 처리:
 * - 시작: GET /api/v1/auth/oauth2/authorize/{provider}
 * - 콜백: GET /api/v1/auth/oauth2/callback/{provider}
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "인증 API")
class AuthController(
    private val authService: AuthService,
    @Value("\${server.port:10010}") private val serverPort: String
) {

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "사용자 ID와 비밀번호로 로그인")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }

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

    @GetMapping("/oauth2/urls")
    @Operation(summary = "OAuth2 로그인 URL 목록", description = "프론트엔드에서 사용할 OAuth2 로그인 URL 제공")
    fun getOAuth2Urls(): ResponseEntity<Map<String, String>> {
        val baseUrl = "http://localhost:$serverPort"
        return ResponseEntity.ok(mapOf(
            "google" to "$baseUrl/api/v1/auth/oauth2/authorize/google",
            "naver" to "$baseUrl/api/v1/auth/oauth2/authorize/naver"
        ))
    }
}
