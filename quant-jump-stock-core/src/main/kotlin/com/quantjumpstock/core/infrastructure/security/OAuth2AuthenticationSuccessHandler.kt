package com.quantjumpstock.core.infrastructure.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class OAuth2AuthenticationSuccessHandler(
    private val jwtService: JwtService,
    @Value("\${oauth2.frontend-redirect-url}") private val frontendRedirectUrl: String
) : SimpleUrlAuthenticationSuccessHandler() {

    private val log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler::class.java)

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oauth2User = authentication.principal as OAuth2User

        val userId = oauth2User.getAttribute<String>("internal_user_id")
            ?: throw IllegalStateException("OAuth2User에 internal_user_id가 없습니다")
        val dbId = oauth2User.getAttribute<Long>("internal_user_db_id")
        val role = oauth2User.getAttribute<String>("internal_user_role") ?: "USER"
        val email = oauth2User.getAttribute<String>("internal_user_email")

        val jwt = jwtService.generateToken(userId, email, role, dbId)

        val redirectUrl = "$frontendRedirectUrl?token=$jwt"
        log.info("OAuth2 로그인 성공: userId=$userId, redirecting to frontend")

        response.sendRedirect(redirectUrl)
    }
}
