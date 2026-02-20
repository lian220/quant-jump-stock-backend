package com.quantjumpstock.core.adapter.input.rest.user

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.user.SavePreferencesRequest
import com.quantjumpstock.core.application.user.UserPreferencesService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 사용자 투자 성향 Controller
 */
@RestController
@RequestMapping("/api/v1/user/preferences")
@Tag(name = "UserPreferences", description = "사용자 투자 성향 API")
class UserPreferencesController(
    private val authService: AuthService,
    private val userPreferencesService: UserPreferencesService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping
    @Operation(summary = "투자 성향 조회", description = "로그인된 사용자의 투자 성향 조회")
    fun getPreferences(
        @RequestHeader("Authorization") authorization: String?
    ): ResponseEntity<Map<String, Any?>> {
        val userPk = authService.resolveUserPk(authorization ?: "")
            ?: return ResponseEntity.status(401).body(
                mapOf("success" to false, "message" to "인증 토큰이 필요합니다.")
            )

        val preferences = userPreferencesService.getPreferences(userPk)

        return ResponseEntity.ok(mapOf(
            "success" to true,
            "preferences" to preferences
        ))
    }

    @PutMapping
    @Operation(summary = "투자 성향 저장", description = "로그인된 사용자의 투자 성향 저장")
    fun savePreferences(
        @RequestHeader("Authorization") authorization: String?,
        @RequestBody request: SavePreferencesRequest
    ): ResponseEntity<Map<String, Any?>> {
        val userPk = authService.resolveUserPk(authorization ?: "")
            ?: return ResponseEntity.status(401).body(
                mapOf("success" to false, "message" to "인증 토큰이 필요합니다.")
            )

        return try {
            val saved = userPreferencesService.savePreferences(userPk, request)
            ResponseEntity.ok(mapOf("success" to true, "preferences" to saved))
        } catch (e: Exception) {
            logger.error("투자 성향 저장 실패: userId=$userPk", e)
            ResponseEntity.internalServerError().body(
                mapOf("success" to false, "message" to "저장에 실패했습니다.")
            )
        }
    }
}
