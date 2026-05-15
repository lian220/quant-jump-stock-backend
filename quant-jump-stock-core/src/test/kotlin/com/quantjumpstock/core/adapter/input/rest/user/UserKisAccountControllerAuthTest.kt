package com.quantjumpstock.core.adapter.input.rest.user

import com.fasterxml.jackson.databind.ObjectMapper
import com.quantjumpstock.core.application.user.KisAccountRequest
import com.quantjumpstock.core.domain.model.user.KisAccountType
import com.quantjumpstock.core.infrastructure.security.JwtService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Phase 1A PRE-요구사항 Task 9 — UserKisAccountController 인증/본인검증(BOLA) 활성화 검증.
 *
 * Step 1 시점에서는 controller 의 validateUserAccess 가 dev-mode 로깅만 수행하므로
 * 타인 호출 403 테스트가 실패한다. Step 3 (UnauthorizedException/AccessDeniedException
 * throw 로 교체) 이후 통과한다.
 *
 * 인증 토큰은 실제 JwtService 로 발급해 Authorization: Bearer 헤더로 전달 — Spring
 * Security 필터 체인을 실제로 통과시켜 가장 사실에 가까운 검증을 한다 (spring-security-test
 * jwt() 후처리기는 본 프로젝트의 커스텀 JwtAuthenticationFilter 를 우회한다).
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserKisAccountControllerAuthTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtService: JwtService

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private fun bearer(userId: String, dbId: Long = 1L): String =
        "Bearer ${jwtService.generateToken(userId, "$userId@example.com", "USER", dbId)}"

    @Test
    fun `비로그인 GET 은 401 (Spring Security 회귀 가드)`() {
        mockMvc.perform(get("/api/v1/users/some-user/kis-accounts"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `타인 userId GET 은 403 (BOLA 차단)`() {
        mockMvc.perform(
            get("/api/v1/users/victim-user/kis-accounts")
                .header("Authorization", bearer("attacker-user"))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `타인 userId POST 는 403 (등록 차단)`() {
        val body = objectMapper.writeValueAsString(
            KisAccountRequest(
                appKey = "PSKxATTACK",
                appSecret = "STOLEN_SECRET",
                accountNumber = "00000000",
                accountProductCode = "01",
                accountType = KisAccountType.MOCK,
                enabled = true
            )
        )
        mockMvc.perform(
            post("/api/v1/users/victim-user/kis-accounts")
                .header("Authorization", bearer("attacker-user"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `타인 userId PATCH toggle 은 403 (활성화 토글 차단)`() {
        mockMvc.perform(
            patch("/api/v1/users/victim-user/kis-accounts/toggle")
                .param("enabled", "false")
                .header("Authorization", bearer("attacker-user"))
        ).andExpect(status().isForbidden)
    }

    // ─────────────────────────────────────────────────
    //  A+ 신규 엔드포인트 BOLA 회귀 가드
    // ─────────────────────────────────────────────────

    @Test
    fun `타인 userId DELETE 는 403 (휴지통으로 이동 차단)`() {
        mockMvc.perform(
            delete("/api/v1/users/victim-user/kis-accounts")
                .header("Authorization", bearer("attacker-user"))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `타인 userId GET trashed 는 403 (휴지통 조회 차단)`() {
        mockMvc.perform(
            get("/api/v1/users/victim-user/kis-accounts/trashed")
                .header("Authorization", bearer("attacker-user"))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `타인 userId POST restore 는 403 (복원 차단)`() {
        mockMvc.perform(
            post("/api/v1/users/victim-user/kis-accounts/restore")
                .header("Authorization", bearer("attacker-user"))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `비로그인 DELETE 는 401`() {
        mockMvc.perform(delete("/api/v1/users/some-user/kis-accounts"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `비로그인 POST restore 는 401`() {
        mockMvc.perform(post("/api/v1/users/some-user/kis-accounts/restore"))
            .andExpect(status().isUnauthorized)
    }
}
