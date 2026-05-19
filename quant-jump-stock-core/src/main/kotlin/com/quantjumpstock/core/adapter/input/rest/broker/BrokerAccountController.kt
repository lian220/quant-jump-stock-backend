package com.quantjumpstock.core.adapter.input.rest.broker

import com.quantjumpstock.core.application.broker.BrokerAccountListResponse
import com.quantjumpstock.core.application.broker.BrokerAccountRequest
import com.quantjumpstock.core.application.broker.BrokerAccountResponse
import com.quantjumpstock.core.application.broker.BrokerAccountUpdateRequest
import com.quantjumpstock.core.application.broker.ForbiddenException
import com.quantjumpstock.core.application.broker.TrashExpiredException
import com.quantjumpstock.core.application.broker.UserBrokerAccountService
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
 * Broker 계좌 관리 Controller (Phase 1B v2.1).
 *
 * 다중 broker × N계좌 지원. 4-tuple (user_id, broker, account_type, account_number) 단위 활성.
 * 사용자가 KIS + Toss × MOCK/REAL × 계좌번호 N개 동시 보유 가능.
 *
 * 보안:
 *  - JWT 인증 필수 (Spring Security 가 `/api/v1/users` 하위 경로 보호)
 *  - path 의 userId 와 인증 user 가 일치해야 (BOLA 차단)
 *  - service 가 accountId 의 ownership 추가 검증 (IDOR 차단)
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/broker-accounts")
class BrokerAccountController(
    private val brokerAccountService: UserBrokerAccountService,
) {

    @GetMapping
    fun listAll(
        @PathVariable userId: String,
        @CurrentUser currentUser: UserPrincipal?,
    ): ResponseEntity<BrokerAccountListResponse> {
        validateUserAccess(userId, currentUser)
        return ResponseEntity.ok(brokerAccountService.listAll(userId))
    }

    @PostMapping
    fun register(
        @PathVariable userId: String,
        @Valid @RequestBody request: BrokerAccountRequest,
        @CurrentUser currentUser: UserPrincipal?,
    ): ResponseEntity<BrokerAccountResponse> {
        validateUserAccess(userId, currentUser)
        val created = brokerAccountService.register(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @GetMapping("/{accountId}")
    fun getOne(
        @PathVariable userId: String,
        @PathVariable accountId: Long,
        @CurrentUser currentUser: UserPrincipal?,
    ): ResponseEntity<BrokerAccountResponse> {
        validateUserAccess(userId, currentUser)
        return ResponseEntity.ok(brokerAccountService.getOne(userId, accountId))
    }

    @PatchMapping("/{accountId}")
    fun update(
        @PathVariable userId: String,
        @PathVariable accountId: Long,
        @Valid @RequestBody request: BrokerAccountUpdateRequest,
        @CurrentUser currentUser: UserPrincipal?,
    ): ResponseEntity<BrokerAccountResponse> {
        validateUserAccess(userId, currentUser)
        return ResponseEntity.ok(brokerAccountService.update(userId, accountId, request))
    }

    @PatchMapping("/{accountId}/toggle")
    fun toggle(
        @PathVariable userId: String,
        @PathVariable accountId: Long,
        @RequestParam enabled: Boolean,
        @CurrentUser currentUser: UserPrincipal?,
    ): ResponseEntity<BrokerAccountResponse> {
        validateUserAccess(userId, currentUser)
        return ResponseEntity.ok(brokerAccountService.toggle(userId, accountId, enabled))
    }

    @DeleteMapping("/{accountId}")
    fun softDelete(
        @PathVariable userId: String,
        @PathVariable accountId: Long,
        @CurrentUser currentUser: UserPrincipal?,
    ): ResponseEntity<Void> {
        validateUserAccess(userId, currentUser)
        brokerAccountService.softDelete(userId, accountId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{accountId}/restore")
    fun restore(
        @PathVariable userId: String,
        @PathVariable accountId: Long,
        @CurrentUser currentUser: UserPrincipal?,
    ): ResponseEntity<BrokerAccountResponse> {
        validateUserAccess(userId, currentUser)
        return ResponseEntity.ok(brokerAccountService.restore(userId, accountId))
    }

    @ExceptionHandler(TrashExpiredException::class)
    fun handleTrashExpired(e: TrashExpiredException): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(HttpStatus.GONE).body(
            mapOf(
                "success" to false,
                "message" to (e.message ?: "휴지통 보관 기간이 만료되었습니다"),
                "code" to "TRASH_EXPIRED",
            ),
        )

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(e: ForbiddenException): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            mapOf(
                "success" to false,
                "message" to (e.message ?: "접근 권한이 없습니다"),
                "code" to "FORBIDDEN",
            ),
        )

    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(e: IllegalStateException): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            mapOf(
                "success" to false,
                "message" to (e.message ?: "충돌이 발생했습니다"),
                "code" to "CONFLICT",
            ),
        )

    private fun validateUserAccess(requestedUserId: String, currentUser: UserPrincipal?) {
        if (currentUser == null) {
            throw UnauthorizedException("Authentication required. Please login first.")
        }
        if (!SecurityUtils.isAdmin() && currentUser.userId != requestedUserId) {
            throw AccessDeniedException("You can only access your own broker accounts.")
        }
    }
}
