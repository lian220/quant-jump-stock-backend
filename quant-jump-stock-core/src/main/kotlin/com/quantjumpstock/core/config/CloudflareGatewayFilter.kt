package com.quantjumpstock.core.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Cloudflare 게이트웨이 시크릿 검증 필터.
 *
 * Cloud Run이 --allow-unauthenticated로 공개 배포될 때,
 * .run.app 도메인 직접 접근을 차단하고 Cloudflare 경유만 허용한다.
 *
 * - cloudflare.gateway-secret이 비어 있으면 필터 비활성 (로컬 개발)
 * - 제외 경로: /actuator/, /_ah/push-handler/, /api/internal/, /api/v1/internal/
 */
@Component
class CloudflareGatewayFilter(
    @Value("\${cloudflare.gateway-secret:}")
    private val gatewaySecret: String
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(CloudflareGatewayFilter::class.java)

    private val excludedPrefixes = listOf(
        "/actuator/",
        "/_ah/push-handler/",
        "/api/internal/",
        "/api/v1/internal/"
    )

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (gatewaySecret.isBlank()) return true
        val path = request.requestURI
        return excludedPrefixes.any { path.startsWith(it) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val headerValue = request.getHeader("X-Cloudflare-Secret")

        if (headerValue == null || headerValue != gatewaySecret) {
            log.warn("Cloudflare 시크릿 불일치 — IP={}, URI={}", request.remoteAddr, request.requestURI)
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = "UTF-8"
            response.writer.write("""{"success":false,"message":"Forbidden","status":403}""")
            return
        }

        filterChain.doFilter(request, response)
    }
}
