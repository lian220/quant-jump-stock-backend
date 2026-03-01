package com.quantjumpstock.core.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * RestClient 설정
 * REST API 호출을 위한 RestClient 빈을 제공합니다.
 */
@Configuration
class RestClientConfig {

    @Bean
    fun restClient(): RestClient {
        return RestClient.builder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.USER_AGENT, "quantiq-core/1.0")
            .build()
    }
}
