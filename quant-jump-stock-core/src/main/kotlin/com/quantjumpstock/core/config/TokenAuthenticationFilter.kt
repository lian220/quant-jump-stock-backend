package com.quantjumpstock.core.config

import com.quantjumpstock.core.application.auth.AuthService
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.infrastructure.security.UserPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 토큰 기반 인증 필터
 * Authorization: Bearer {token} 헤더를 검증하여 SecurityContext에 인증 정보를 설정합니다.
 */
@Component
class TokenAuthenticationFilter(
    private val authService: AuthService,
    private val userJpaRepository: UserJpaRepository
) : OncePerRequestFilter() {

    private val log = org.slf4j.LoggerFactory.getLogger(TokenAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val token = extractToken(request)
            if (token != null) {
                val loginResponse = authService.validateToken(token)
                if (loginResponse != null && loginResponse.user != null) {
                    val userEntity = userJpaRepository.findByUserId(loginResponse.user.userId).orElse(null)
                    if (userEntity != null) {
                        val principal = UserPrincipal.from(userEntity)
                        val authentication = UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.authorities
                        )
                        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                        SecurityContextHolder.getContext().authentication = authentication
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("인증 처리 중 오류 발생: ${e.message}", e)
            SecurityContextHolder.clearContext()
        }

        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        return if (header.startsWith("Bearer ")) {
            header.removePrefix("Bearer ")
        } else {
            null
        }
    }
}
