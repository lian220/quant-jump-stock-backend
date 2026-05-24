package com.quantjumpstock.core.adapter.input.rest.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.quantjumpstock.core.application.auth.RefreshTokenService
import com.quantjumpstock.core.domain.model.user.User
import com.quantjumpstock.core.domain.model.user.UserRole
import com.quantjumpstock.core.domain.model.user.UserStatus
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.domain.port.output.UserTierRepository
import com.quantjumpstock.core.infrastructure.security.JwtService
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * /auth/refresh + cookie 발급/revoke 통합 테스트.
 *
 * Phase 1A 보안 PRE Task 12:
 * - 정상 refresh cookie → 새 access token 200
 * - cookie 누락/잘못된 cookie → 401
 * - access token 을 refresh 자리에서 사용 → 401 (Token Confusion)
 * - logout 후 같은 cookie → 401 (jti revoke 검증)
 * - Origin 외부 또는 누락 → 403 (CSRF 임시 방어)
 *
 * 실제 Spring Security 필터 체인을 사용하기 위해 spring-security-test 의 jwt() 후처리기 대신
 * 실제 JwtService 로 발급된 토큰을 Authorization 헤더에 직접 주입.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthRefreshControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtService: JwtService
    @Autowired lateinit var refreshTokenService: RefreshTokenService
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var userTierRepository: UserTierRepository
    @Autowired lateinit var passwordEncoder: PasswordEncoder
    @Autowired lateinit var objectMapper: ObjectMapper

    private lateinit var testUser: User
    private val testPassword = "refreshtest123!"

    @BeforeEach
    fun setUp() {
        val userId = "refresh_test_${System.currentTimeMillis()}"
        val savedUser = userRepository.save(
            User(
                userId = userId,
                email = "$userId@test.com",
                name = "refresh test",
                passwordHash = passwordEncoder.encode(testPassword),
                status = UserStatus.ACTIVE,
                role = UserRole.USER,
            )
        )
        // 무료 티어 생성 (signup 흐름과 동일)
        runCatching { userTierRepository.createFreeTierForUser(savedUser.userId) }
        testUser = savedUser
    }

    @Test
    fun `valid refresh cookie 로 새 access token 발급`() {
        val refresh = issueRefresh()

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .cookie(Cookie("refresh_token", refresh))
                .header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
    }

    @Test
    fun `refresh cookie 누락은 401`() {
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .header("Origin", "http://localhost:3000")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `잘못된 refresh cookie 는 401`() {
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .cookie(Cookie("refresh_token", "invalid.jwt.token"))
                .header("Origin", "http://localhost:3000")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `Token Confusion 방어 - access token 을 refresh 자리에서 사용하면 401`() {
        @Suppress("DEPRECATION")
        val accessToken = jwtService.generateAccessToken(testUser.userId, testUser.email, "USER", testUser.id)

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .cookie(Cookie("refresh_token", accessToken))
                .header("Origin", "http://localhost:3000")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `logout 후 같은 refresh cookie 는 401 (revoke 검증)`() {
        val refresh = issueRefresh()

        // logout — access token 으로 사용자 식별 → user 의 모든 refresh revoke
        val access = jwtService.generateAccessToken(testUser.userId, testUser.email, "USER", testUser.id)
        mockMvc.perform(
            post("/api/v1/auth/logout")
                .header("Authorization", "Bearer $access")
        ).andExpect(status().isOk)

        // 이전 refresh cookie 재사용 시도 → 401
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .cookie(Cookie("refresh_token", refresh))
                .header("Origin", "http://localhost:3000")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `Origin 헤더 누락은 403`() {
        val refresh = issueRefresh()

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .cookie(Cookie("refresh_token", refresh))
            // Origin/Referer 모두 없음
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `외부 Origin 은 403`() {
        val refresh = issueRefresh()

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .cookie(Cookie("refresh_token", refresh))
                .header("Origin", "https://evil.example.com")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `login 응답에 refresh_token httpOnly cookie 가 Set-Cookie 헤더로 발급된다`() {
        val body = """{"userId":"${testUser.userId}","password":"$testPassword"}"""
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(cookie().exists("refresh_token"))
            .andExpect(cookie().httpOnly("refresh_token", true))
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").doesNotExist())  // JSON 미노출 검증
            .andExpect(jsonPath("$.userDbId").doesNotExist())
    }

    @Test
    fun `logout 응답에 Set-Cookie 만료 헤더가 포함된다`() {
        val access = jwtService.generateAccessToken(testUser.userId, testUser.email, "USER", testUser.id)

        mockMvc.perform(
            post("/api/v1/auth/logout")
                .header("Authorization", "Bearer $access")
        )
            .andExpect(status().isOk)
            .andExpect(cookie().exists("refresh_token"))
            .andExpect(cookie().maxAge("refresh_token", 0))
    }

    private fun issueRefresh(): String =
        refreshTokenService.issue(testUser.userId, testUser.id!!)
}
