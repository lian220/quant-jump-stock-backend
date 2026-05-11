package com.quantjumpstock.core.adapter.input.rest.user

import com.quantjumpstock.core.application.user.UserKisAccountService
import com.quantjumpstock.core.application.user.KisAccountRequest
import com.quantjumpstock.core.application.user.KisAccountResponse
import com.quantjumpstock.core.infrastructure.security.CurrentUser
import com.quantjumpstock.core.infrastructure.security.UserPrincipal
import com.quantjumpstock.core.infrastructure.security.SecurityUtils
import com.quantjumpstock.core.infrastructure.security.AccessDeniedException
import com.quantjumpstock.core.infrastructure.security.UnauthorizedException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * User KIS 계정 관리 Controller
 * 사용자별 한국투자증권 계정 정보 등록/조회/관리
 *
 * 보안: 본인만 자신의 KIS 계정 정보에 접근 가능 (Phase 1A PRE Task 9 — BOLA 차단)
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/kis-accounts")
class UserKisAccountController(
    private val userKisAccountService: UserKisAccountService
) {

    @PostMapping
    fun registerKisAccount(
        @PathVariable userId: String,
        @RequestBody request: KisAccountRequest,
        @CurrentUser currentUser: UserPrincipal?
    ): ResponseEntity<Map<String, Any>> {
        validateUserAccess(userId, currentUser)

        val kisAccount = userKisAccountService.registerOrUpdateKisAccount(userId, request)

        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "KIS account registered successfully",
            "accountNumber" to kisAccount.accountNumber,
            "accountType" to kisAccount.accountType.name
        ))
    }

    @GetMapping
    fun getKisAccount(
        @PathVariable userId: String,
        @CurrentUser currentUser: UserPrincipal?
    ): ResponseEntity<KisAccountResponse> {
        validateUserAccess(userId, currentUser)

        val response = userKisAccountService.getKisAccount(userId)
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/toggle")
    fun toggleKisAccount(
        @PathVariable userId: String,
        @RequestParam enabled: Boolean,
        @CurrentUser currentUser: UserPrincipal?
    ): ResponseEntity<Map<String, Any>> {
        validateUserAccess(userId, currentUser)

        userKisAccountService.toggleKisAccount(userId, enabled)

        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "KIS account ${if (enabled) "enabled" else "disabled"}",
            "enabled" to enabled
        ))
    }

    // 본인 확인 검증 (Phase 1A PRE Task 9 — BOLA 차단 활성화).
    //
    // Spring Security 가 /api/v1/users 하위 경로를 authenticated() 로 보호하므로
    // 비로그인 호출은 이 메서드 진입 전에 401 로 거절된다. 본 메서드는 인증된
    // 사용자가 다른 사용자의 자원에 접근하는 BOLA 시도를 403 으로 차단한다.
    private fun validateUserAccess(requestedUserId: String, currentUser: UserPrincipal?) {
        if (currentUser == null) {
            throw UnauthorizedException("Authentication required. Please login first.")
        }
        if (!SecurityUtils.isAdmin() && currentUser.userId != requestedUserId) {
            throw AccessDeniedException("You can only access your own KIS account information.")
        }
    }
}
