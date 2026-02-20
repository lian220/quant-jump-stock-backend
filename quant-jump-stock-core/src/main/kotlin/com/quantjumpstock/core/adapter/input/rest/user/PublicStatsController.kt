package com.quantjumpstock.core.adapter.input.rest.user

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 인증 없이 공개되는 플랫폼 통계 API
 */
@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "Stats", description = "공개 플랫폼 통계 API")
class PublicStatsController(
    private val userJpaRepository: UserJpaRepository,
) {

    @GetMapping("/public")
    @Operation(summary = "공개 플랫폼 통계 조회", description = "가입자 수 등 공개 통계를 반환합니다.")
    fun getPublicStats(): ResponseEntity<Map<String, Any>> {
        val userCount = userJpaRepository.countByStatus(UserStatus.ACTIVE)
        return ResponseEntity.ok(
            mapOf(
                "userCount" to userCount,
            )
        )
    }
}
