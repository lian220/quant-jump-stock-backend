package com.quantjumpstock.core.config

import com.quantjumpstock.core.infrastructure.security.JwtService
import com.quantjumpstock.core.infrastructure.security.UserPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val token = extractToken(request)
            if (token != null) {
                val claims = jwtService.validateToken(token)
                if (claims != null) {
                    val principal = UserPrincipal(
                        id = claims.dbId ?: 0,
                        userId = claims.userId,
                        email = claims.email,
                        password = "",
                        roles = setOf(claims.role),
                        enabled = true
                    )

                    val authentication = UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.authorities
                    )
                    authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = authentication
                }
            }
        } catch (e: Exception) {
            log.warn("JWT 인증 처리 중 오류: ${e.message}")
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
