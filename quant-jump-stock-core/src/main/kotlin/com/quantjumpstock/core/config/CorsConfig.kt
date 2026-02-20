package com.quantjumpstock.core.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig {

    @Value("\${app.cors.allowed-origins:http://localhost:3000,http://localhost:4000,http://localhost:4010}")
    private lateinit var allowedOrigins: String

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        val origins = allowedOrigins.split(",").map { it.trim() }
        // allowCredentials=true와 wildcard "*"는 함께 사용 불가 → allowedOriginPatterns 사용
        if (origins.contains("*")) {
            config.allowedOriginPatterns = listOf("*")
        } else {
            config.allowedOrigins = origins
        }
        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        config.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/**", config)
        return source
    }
}
