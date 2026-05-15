package com.quantjumpstock.core.adapter.input.rest.user

import com.quantjumpstock.core.application.user.KisAccountRequest
import com.quantjumpstock.core.application.user.KisAccountResponse
import com.quantjumpstock.core.application.user.TrashExpiredException
import com.quantjumpstock.core.application.user.UserKisAccountService
import com.quantjumpstock.core.infrastructure.security.AccessDeniedException
import com.quantjumpstock.core.infrastructure.security.CurrentUser
import com.quantjumpstock.core.infrastructure.security.SecurityUtils
import com.quantjumpstock.core.infrastructure.security.UnauthorizedException
import com.quantjumpstock.core.infrastructure.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * User KIS 계정 관리 Controller.
 *
 * A+ 모델 (단일 활성 + 7일 휴지통):
 * - `POST /` 모드 변경 시 기존 활성을 자동 soft delete (모드 전환).
 * - `DELETE /` 활성을 휴지통으로.
 * - `GET /trashed` 휴지통 row 조회.
 * - `POST /restore` 휴지통 → 활성 스왑.
 *
 * 보안: 본인만 자신의 KIS 계정 정보에 접근 가능 (Phase 1A PRE Task 9 — BOLA 차단).
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/kis-accounts")
class UserKisAccountController(
    private val userKisAccountService: UserKisAccountService
) {

    @PostMapping
    fun registerKisAccount(
        @PathVariable userId: String,
        @Valid @RequestBody request: KisAccountRequest,
        @CurrentUser currentUser: UserPrincipal?
    ): ResponseEntity<KisAccountResponse> {
        validateUserAccess(userId, currentUser)
        userKisAccountService.registerOrUpdateKisAccount(userId, request)
        // 응답은 도메인 서비스 조회 메서드를 한 번 더 거쳐 마스킹 일관성 보장.
        return ResponseEntity.ok(userKisAccountService.getKisAccount(userId))
    }

    @GetMapping
    fun getKisAccount(
        @PathVariable userId: String,
        @CurrentUser currentUser: UserPrincipal?
    ): ResponseEntity<KisAccountResponse> {
        validateUserAccess(userId, currentUser)
        return ResponseEntity.ok(userKisAccountService.getKisAccount(userId))
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

    /** A+ — 활성 row 를 휴지통으로 이동 (soft delete). 영구 삭제는 7일 후 Cloud Scheduler 가 처리. */
    @DeleteMapping
    fun deleteKisAccount(
        @PathVariable userId: String,
        @CurrentUser currentUser: UserPrincipal?
    ): ResponseEntity<Void> {
        validateUserAccess(userId, currentUser)
        userKisAccountService.softDeleteActiveKisAccount(userId)
        return ResponseEntity.noContent().build()
    }

    /** A+ — 휴지통 row 조회. 없으면 404. */
    @GetMapping("/trashed")
    fun getTrashedKisAccount(
        @PathVariable userId: String,
        @CurrentUser currentUser: UserPrincipal?
    ): ResponseEntity<KisAccountResponse> {
        validateUserAccess(userId, currentUser)
        val response = userKisAccountService.getTrashedKisAccount(userId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(response)
    }

    /** A+ — 휴지통 → 활성 스왑. 7일 만료 시 410 Gone. */
    @PostMapping("/restore")
    fun restoreKisAccount(
        @PathVariable userId: String,
        @CurrentUser currentUser: UserPrincipal?
    ): ResponseEntity<KisAccountResponse> {
        validateUserAccess(userId, currentUser)
        return ResponseEntity.ok(userKisAccountService.restoreFromTrash(userId))
    }

    /** 휴지통 보관 기간 만료 → 410 Gone. */
    @ExceptionHandler(TrashExpiredException::class)
    fun handleTrashExpired(e: TrashExpiredException): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.status(HttpStatus.GONE).body(mapOf(
            "success" to false,
            "message" to (e.message ?: "휴지통 보관 기간이 만료되었습니다"),
            "code" to "TRASH_EXPIRED"
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
