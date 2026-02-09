package com.quantjumpstock.core.config

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
 * TokenAuthenticationFilter를 통해 Bearer 토큰 인증을 수행하고, Admin API에 대한 역할 기반 접근 제어를 적용합니다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(private val tokenAuthenticationFilter: TokenAuthenticationFilter) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
                .csrf { it.disable() }
                .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
                .authorizeHttpRequests { auth ->
                    auth.requestMatchers("/api/v1/admin/**")
                            .hasRole("ADMIN")
                            .requestMatchers(
                                    "/api/v1/auth/**",
                                    "/api/auth/**",
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
                .addFilterBefore(
                        tokenAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter::class.java
                )

        return http.build()
    }
}
