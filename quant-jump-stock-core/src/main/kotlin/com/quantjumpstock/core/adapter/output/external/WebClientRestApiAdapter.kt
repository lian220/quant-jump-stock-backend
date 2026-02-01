package com.quantjumpstock.core.adapter.output.external

import com.quantjumpstock.core.domain.economic.port.output.RestApiClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.util.concurrent.CompletableFuture

/**
 * WebClient REST API Adapter (Output Adapter)
 * RestApiClient 인터페이스를 구현하여 외부 REST API와 연동합니다.
 */
@Component
class WebClientRestApiAdapter(
    private val webClient: WebClient
) : RestApiClient {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun callEconomicDataCollectionApi(url: String, targetDate: String?): CompletableFuture<String> {
        return try {
            val dateInfo = targetDate ?: "당일"
            logger.info("REST API 호출: $url (기준일: $dateInfo)")

            val requestBody = if (targetDate != null) {
                mapOf("target_date" to targetDate)
            } else {
                emptyMap()
            }

            webClient.post()
                .uri(url)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String::class.java)
                .toFuture()
        } catch (e: Exception) {
            logger.error("REST API 호출 실패: $url", e)
            CompletableFuture.failedFuture(e)
        }
    }
}
