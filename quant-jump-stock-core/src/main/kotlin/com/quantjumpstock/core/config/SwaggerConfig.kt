package com.quantjumpstock.core.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("QuantIQ API")
                    .description("QuantIQ 자동 매매 시스템 API 문서")
                    .version("v1.0.0")
                    .contact(
                        Contact()
                            .name("QuantIQ Team")
                            .email("support@quantiq.com")
                    )
                    .license(
                        License()
                            .name("Apache 2.0")
                            .url("https://www.apache.org/licenses/LICENSE-2.0.html")
                    )
            )
            .servers(
                listOf(
                    Server()
                        .url("http://localhost:8080")
                        .description("로컬 개발 서버"),
                    Server()
                        .url("http://localhost:10010")
                        .description("Docker 개발 서버")
                )
            )
    }
}
