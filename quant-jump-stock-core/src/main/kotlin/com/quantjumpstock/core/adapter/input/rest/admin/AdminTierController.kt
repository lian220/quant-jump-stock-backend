package com.quantjumpstock.core.adapter.input.rest.admin

import com.quantjumpstock.core.application.tier.TierConfigurationDto
import com.quantjumpstock.core.application.tier.TierConfigurationService
import com.quantjumpstock.core.application.tier.UpdateTierConfigRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * 어드민용 티어 설정 관리 API (SCRUM-168 확장)
 * GET   /api/v1/admin/tier-configurations
 * GET   /api/v1/admin/tier-configurations/{tier}
 * PATCH /api/v1/admin/tier-configurations/{tier}
 */
@RestController
@RequestMapping("/api/v1/admin/tier-configurations")
@Tag(name = "Admin Tier", description = "관리자용 티어 설정 관리 API")
@PreAuthorize("hasRole('ADMIN')")
class AdminTierController(
    private val tierConfigService: TierConfigurationService
) {

    @GetMapping
    @Operation(summary = "전체 티어 설정 조회", description = "FREE, PREMIUM, PREMIUM_YEARLY 설정을 모두 조회합니다.")
    fun getAllConfigurations(): ResponseEntity<List<TierConfigurationDto>> {
        return ResponseEntity.ok(tierConfigService.getAllConfigurations())
    }

    @GetMapping("/{tier}")
    @Operation(summary = "단건 티어 설정 조회")
    fun getConfiguration(@PathVariable tier: String): ResponseEntity<Any> {
        val configs = tierConfigService.getAllConfigurations()
        val config = configs.find { it.tier.equals(tier, ignoreCase = true) }
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(config)
    }

    @PatchMapping("/{tier}")
    @Operation(summary = "티어 설정 변경", description = "특정 티어의 구독/백테스트 제한값을 변경합니다.")
    fun updateConfiguration(
        @PathVariable tier: String,
        @RequestBody request: UpdateTierConfigRequest,
        authentication: Authentication
    ): ResponseEntity<Any> {
        return try {
            val updatedBy = authentication.name ?: "admin"
            val result = tierConfigService.updateConfiguration(tier.uppercase(), request, updatedBy)
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }
}
