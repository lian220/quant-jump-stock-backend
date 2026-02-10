package com.quantjumpstock.core.config

import com.quantjumpstock.core.infrastructure.security.CustomOAuth2UserService
import com.quantjumpstock.core.infrastructure.security.OAuth2AuthenticationSuccessHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Spring Security 설정
 *
 * JWT 인증 필터 + Spring Security OAuth2 Client로 Google/Naver 로그인을 처리합니다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val oAuth2SuccessHandler: OAuth2AuthenticationSuccessHandler
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
                .csrf { it.disable() }
                .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
                .authorizeHttpRequests { auth ->
                    auth.requestMatchers("/api/v1/admin/**")
                            .hasRole("ADMIN")
                            .requestMatchers(
                                    "/api/v1/auth/**",
                                    "/api/auth/**",
                                    "/api/v1/auth/oauth2/**",
                                    "/swagger-ui/**",
                                    "/v3/api-docs/**"
                            )
                            .permitAll()
                            .requestMatchers("/api/v1/portfolios/**")
                            .authenticated()
                            .requestMatchers("/api/v1/strategies/*/default-stocks/**")
                            .authenticated()
                            .anyRequest()
                            .permitAll()
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
