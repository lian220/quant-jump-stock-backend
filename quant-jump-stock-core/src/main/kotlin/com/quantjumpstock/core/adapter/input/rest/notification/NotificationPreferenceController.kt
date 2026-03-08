package com.quantjumpstock.core.adapter.input.rest.notification

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.notification.NotificationPreferenceDto
import com.quantjumpstock.core.application.notification.NotificationPreferenceService
import com.quantjumpstock.core.application.notification.NotificationPreferenceUpdate
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/user/notification-preferences")
@Tag(name = "Notification Preferences", description = "알림 설정 API")
class NotificationPreferenceController(
    private val preferenceService: NotificationPreferenceService,
    private val authService: AuthService
) {

    @GetMapping
    @Operation(summary = "알림 설정 조회", description = "카테고리별 알림 on/off 설정을 조회합니다")
    fun getPreferences(
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<NotificationPreferenceDto> {
        val userId = resolveUserIdOrThrow(authorization)
        val preferences = preferenceService.getPreferences(userId)
        return ResponseEntity.ok(NotificationPreferenceDto.from(preferences))
    }

    @PatchMapping
    @Operation(summary = "알림 설정 변경", description = "변경할 항목만 전달합니다 (partial update)")
    fun updatePreferences(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody update: NotificationPreferenceUpdate
    ): ResponseEntity<NotificationPreferenceDto> {
        val userId = resolveUserIdOrThrow(authorization)
        val updated = preferenceService.updatePreferences(userId, update)
        return ResponseEntity.ok(NotificationPreferenceDto.from(updated))
    }

    private fun resolveUserIdOrThrow(authorization: String): Long {
        return authService.resolveUserPk(authorization)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다")
    }
}
