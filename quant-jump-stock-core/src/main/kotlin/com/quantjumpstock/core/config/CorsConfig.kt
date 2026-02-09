package com.quantjumpstock.core.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 글로벌 CORS 설정
 *
 * 환경변수 CORS_ALLOWED_ORIGINS로 허용 도메인을 제어합니다.
 * 로컬: http://localhost:3000,http://localhost:4000
 * 운영: https://alphafoundry.app,https://admin.alphafoundry.app
 */
@Configuration
class CorsConfig(
    @Value("\${app.cors.allowed-origins:http://localhost:3000,http://localhost:4000}")
    private val allowedOrigins: String
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        val origins = parseOrigins()

        registry.addMapping("/**")
            .allowedOrigins(*origins)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600)
    }

    /**
     * 환경변수에서 CORS origins를 파싱합니다.
     * 쉼표로 구분된 문자열을 배열로 변환합니다.
     */
    private fun parseOrigins(): Array<String> {
        return allowedOrigins.split(",").map { it.trim() }.toTypedArray()
    }
}
