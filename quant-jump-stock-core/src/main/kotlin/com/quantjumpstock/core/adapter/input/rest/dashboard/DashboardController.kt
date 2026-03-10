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
        @RequestHeader("Authorization", required = false) authorization: String?
    ): ResponseEntity<Any> {
        if (authorization == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SimpleResponse(false, "인증이 필요합니다"))
        }

        val resolvedUser = authService.resolveUser(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SimpleResponse(false, "인증이 필요합니다"))

        val dashboard = dashboardService.getDashboard(resolvedUser.userId, resolvedUser.userDbId)
        return ResponseEntity.ok(dashboard)
    }
}
