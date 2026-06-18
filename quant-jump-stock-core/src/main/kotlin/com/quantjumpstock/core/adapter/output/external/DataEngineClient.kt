package com.quantjumpstock.core.adapter.output.external

import com.quantjumpstock.core.application.stock.PriceHistoryPort
import com.quantjumpstock.core.application.stock.PriceHistoryResponse
import com.quantjumpstock.core.domain.model.stock.PriceHistoryPeriod
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.Duration

/**
 * Data Engine API 클라이언트 (Output Adapter)
 *
 * data-engine 서비스와 통신하는 어댑터입니다.
 * ML 패키지 업로드 등 Python 기반 기능을 호출합니다.
 */
@Component
class DataEngineClient(
    @Value("\${data-engine.base-url:http://localhost:10020}")
    private val baseUrl: String
) : PriceHistoryPort {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(10))
            setReadTimeout(Duration.ofMinutes(5))
        })
        .build()

    /**
     * ML 패키지 업로드 응답
     */
    data class PackageUploadResponse(
        val success: Boolean,
        val message: String,
        val gcs_uri: String? = null,
        val version: Int? = null,
        val timestamp: String
    )

    /**
     * ML 패키지 최신 버전 정보
     */
    data class LatestPackage(
        val gcs_uri: String? = null,
        val size: Long? = null,
        val updated: String? = null
    )

    /**
     * ML 패키지 상태 응답 (Data Engine 실제 형식)
     */
    data class PackageStatusResponse(
        val bucket: String,
        val base_path: String,
        val current_version: Int,
        val latest_package: LatestPackage? = null,
        val timestamp: String
    )

    /**
     * ML 패키지를 GCS에 업로드
     * data-engine의 /api/v1/ml/upload 엔드포인트 호출
     */
    fun uploadMlPackage(): PackageUploadResponse {
        val url = "/api/v1/ml/upload"
        logger.info("📦 data-engine ML 패키지 업로드 호출: $baseUrl$url")

        return try {
            val response = restClient.post()
                .uri(url)
                .retrieve()
                .body(PackageUploadResponse::class.java)

            if (response != null) {
                logger.info("✅ data-engine 응답: success=${response.success}, gcs_uri=${response.gcs_uri}")
                response
            } else {
                logger.error("❌ data-engine 응답이 null입니다")
                PackageUploadResponse(
                    success = false,
                    message = "data-engine 응답이 null입니다",
                    timestamp = java.time.LocalDateTime.now().toString()
                )
            }
        } catch (e: RestClientResponseException) {
            logger.error("❌ data-engine API 오류: ${e.statusCode} - ${e.responseBodyAsString}", e)
            PackageUploadResponse(
                success = false,
                message = "data-engine API 오류: ${e.message}",
                timestamp = java.time.LocalDateTime.now().toString()
            )
        } catch (e: Exception) {
            logger.error("❌ data-engine 호출 실패: ${e.message}", e)
            PackageUploadResponse(
                success = false,
                message = "data-engine 호출 실패: ${e.message}",
                timestamp = java.time.LocalDateTime.now().toString()
            )
        }
    }

    /**
     * ML 패키지 상태 조회
     * data-engine의 /api/v1/ml/status 엔드포인트 호출
     */
    fun getPackageStatus(): PackageStatusResponse {
        val url = "/api/v1/ml/status"
        logger.info("📋 data-engine ML 패키지 상태 조회: $baseUrl$url")

        return try {
            restClient.get()
                .uri(url)
                .retrieve()
                .body(PackageStatusResponse::class.java)
                ?: PackageStatusResponse(
                    bucket = "unknown",
                    base_path = "unknown",
                    current_version = 0,
                    timestamp = java.time.LocalDateTime.now().toString()
                )
        } catch (e: Exception) {
            logger.error("❌ data-engine 상태 조회 실패: ${e.message}", e)
            PackageStatusResponse(
                bucket = "error",
                base_path = "",
                current_version = 0,
                latest_package = LatestPackage(gcs_uri = "data-engine 상태 조회 실패: ${e.message}"),
                timestamp = java.time.LocalDateTime.now().toString()
            )
        }
    }

    /**
     * 종목 가격 이력 조회
     * data-engine의 /api/v1/stocks/{ticker}/price-history 엔드포인트 호출
     */
    override fun getPriceHistory(ticker: String, period: PriceHistoryPeriod): PriceHistoryResponse {
        logger.info("📈 data-engine price-history 호출: $baseUrl/api/v1/stocks/$ticker/price-history?period=${period.token}")

        return try {
            restClient.get()
                .uri("/api/v1/stocks/{ticker}/price-history?period={period}", ticker, period.token)
                .retrieve()
                .body(PriceHistoryResponse::class.java)
                ?: PriceHistoryResponse(ticker = ticker, period = period.token, candles = emptyList())
        } catch (e: Exception) {
            logger.error("❌ data-engine price-history 실패: ${e.message}", e)
            PriceHistoryResponse(ticker = ticker, period = period.token, candles = emptyList())
        }
    }
}
