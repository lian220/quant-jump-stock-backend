package com.quantjumpstock.core.adapter.output.external

import com.quantjumpstock.core.domain.economic.port.output.RestApiClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.concurrent.CompletableFuture

/**
 * RestClient REST API Adapter (Output Adapter)
 * RestApiClient 인터페이스를 구현하여 외부 REST API와 연동합니다.
 */
@Component
class WebClientRestApiAdapter(
    private val restClient: RestClient
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

            CompletableFuture.supplyAsync {
                restClient.post()
                    .uri(url)
                    .body(requestBody)
                    .retrieve()
                    .body(String::class.java) ?: ""
            }
        } catch (e: Exception) {
            logger.error("REST API 호출 실패: $url", e)
            CompletableFuture.failedFuture(e)
        }
    }
}
