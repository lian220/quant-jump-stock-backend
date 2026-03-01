package com.quantjumpstock.core.config

import com.fasterxml.jackson.databind.ObjectMapper
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
 * JWT 인증 필터를 사용하며, OAuth2는 프론트엔드-first 방식으로 처리합니다.
 * (프론트 → Naver → 프론트 콜백 → 백엔드 POST /api/v1/auth/oauth2/code/{provider})
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val objectMapper: ObjectMapper,
    private val corsConfigurationSource: CorsConfigurationSource
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
                .cors { it.configurationSource(corsConfigurationSource) }
                .csrf { it.disable() }
                .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
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
                                    "/actuator/health/**",
                                    "/actuator/info",
                                    // 공개 API (인증 없이 조회 가능)
                                    "/api/v1/stocks/**",
                                    "/api/v1/predictions/**",
                                    "/api/v1/marketplace/**",
                                    "/api/v1/news/**",
                                    "/api/v1/benchmarks/**",
                                    "/api/v1/categories/**",
                                    "/api/v1/stats/**",
                                    // 내부 서비스 API (Data Engine → Core 캐시 eviction)
                                    "/api/v1/internal/cache/evict",
                                    // Cloud Scheduler HTTP 타겟 (내부 전용)
                                    "/api/internal/scheduler/**",
                                    // Pub/Sub Push 엔드포인트 (Cloud Run OIDC 또는 내부 네트워크)
                                    "/_ah/push-handler/**"
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
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter::class.java
                )

        return http.build()
    }
}
