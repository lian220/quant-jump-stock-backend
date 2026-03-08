package com.quantjumpstock.core.adapter.input.rest.dashboard

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.application.common.SimpleResponse
import com.quantjumpstock.core.application.dashboard.DashboardResponse
import com.quantjumpstock.core.application.dashboard.DashboardService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "대시보드 API")
class DashboardController(
    private val dashboardService: DashboardService,
    private val authService: AuthService
) {

    @GetMapping
    @Operation(summary = "대시보드 조회", description = "로그인 사용자의 대시보드 데이터를 집계하여 반환합니다")
    fun getDashboard(
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<Any> {
        // DB PK (Long) 추출 - Notification 등에서 사용
        val userDbId = authService.resolveUserPk(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SimpleResponse(false, "인증이 필요합니다"))

        // 로그인 ID (String) 추출 - Subscription, UserTier 등에서 사용
        val token = authorization.removePrefix("Bearer ")
        val userId = authService.validateToken(token)?.user?.userId
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SimpleResponse(false, "인증이 필요합니다"))

        val dashboard = dashboardService.getDashboard(userId, userDbId)
        return ResponseEntity.ok(dashboard)
    }
}
