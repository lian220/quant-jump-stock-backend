package com.quantjumpstock.core.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.quantjumpstock.core.infrastructure.security.CustomOAuth2UserService
import com.quantjumpstock.core.infrastructure.security.OAuth2AuthenticationSuccessHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfigurationSource

/**
 * Spring Security 설정
 *
 * JWT 인증 필터 + Spring Security OAuth2 Client로 Google/Naver 로그인을 처리합니다.
 * API 요청에 대해 401 JSON 응답, OAuth2 플로우에 대해서만 리다이렉트를 수행합니다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val oAuth2SuccessHandler: OAuth2AuthenticationSuccessHandler,
    private val objectMapper: ObjectMapper,
    private val corsConfigurationSource: CorsConfigurationSource
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
                .cors { it.configurationSource(corsConfigurationSource) }
                .csrf { it.disable() }
                .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
                .authorizeHttpRequests { auth ->
                    auth.requestMatchers("/api/v1/admin/**")
                            .hasRole("ADMIN")
                            .requestMatchers(
                                    "/api/v1/auth/**",
                                    "/api/auth/**",
                                    "/api/v1/vertex-ai/callback",
                                    "/swagger-ui/**",
                                    "/swagger-ui.html",
                                    "/v3/api-docs/**",
                                    "/api-docs/**",
                                    "/actuator/health",
                                    "/actuator/info",
                                    // 공개 API (인증 없이 조회 가능)
                                    "/api/v1/stocks/**",
                                    "/api/v1/predictions/**",
                                    "/api/v1/marketplace/**",
                                    "/api/v1/news/**",
                                    "/api/v1/benchmarks/**",
                                    "/api/v1/categories/**",
                                    "/api/v1/stats/**"
                            )
                            .permitAll()
                            .anyRequest()
                            .authenticated()
                }
                .exceptionHandling { exceptions ->
                    // API 요청에 대해 302 OAuth 리다이렉트 대신 401 JSON 응답
                    exceptions.authenticationEntryPoint { _, response, _ ->
                        response.status = 401
                        response.contentType = MediaType.APPLICATION_JSON_VALUE
                        response.characterEncoding = "UTF-8"
                        objectMapper.writeValue(
                            response.outputStream,
                            mapOf(
                                "success" to false,
                                "message" to "인증 토큰이 필요합니다.",
                                "status" to 401
                            )
                        )
                    }
                }
                .oauth2Login { oauth2 ->
                    oauth2
                        .authorizationEndpoint { it.baseUri("/api/v1/auth/oauth2/authorize") }
                        .redirectionEndpoint { it.baseUri("/api/v1/auth/oauth2/callback/*") }
                        .userInfoEndpoint { it.userService(customOAuth2UserService) }
                        .successHandler(oAuth2SuccessHandler)
                }
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter::class.java
                )

        return http.build()
    }
}
