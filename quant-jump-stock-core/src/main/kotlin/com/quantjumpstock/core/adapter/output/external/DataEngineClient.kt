package com.quantjumpstock.core.adapter.output.external

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration

/**
 * Data Engine API 클라이언트 (Output Adapter)
 *
 * data-engine 서비스와 통신하는 어댑터입니다.
 * ML 패키지 업로드 등 Python 기반 기능을 호출합니다.
 */
@Component
class DataEngineClient(
    private val webClient: WebClient,
    @Value("\${data-engine.base-url:http://localhost:10020}")
    private val baseUrl: String
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

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
     * ML 패키지 상태 응답
     */
    data class PackageStatusResponse(
        val service: String,
        val status: String,
        val message: String? = null,
        val timestamp: String
    )

    /**
     * ML 패키지를 GCS에 업로드
     * data-engine의 /ml/upload-package 엔드포인트 호출
     */
    fun uploadMlPackage(): PackageUploadResponse {
        val url = "$baseUrl/ml/upload-package"
        logger.info("📦 data-engine ML 패키지 업로드 호출: $url")

        return try {
            val response = webClient.post()
                .uri(url)
                .retrieve()
                .bodyToMono(PackageUploadResponse::class.java)
                .timeout(Duration.ofMinutes(5))
                .block()

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
        } catch (e: WebClientResponseException) {
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
     * data-engine의 /ml/package-status 엔드포인트 호출
     */
    fun getPackageStatus(): PackageStatusResponse {
        val url = "$baseUrl/ml/package-status"
        logger.info("📋 data-engine ML 패키지 상태 조회: $url")

        return try {
            val response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(PackageStatusResponse::class.java)
                .timeout(Duration.ofSeconds(30))
                .block()

            response ?: PackageStatusResponse(
                service = "ml-package-manager",
                status = "unknown",
                message = "data-engine 응답이 null입니다",
                timestamp = java.time.LocalDateTime.now().toString()
            )
        } catch (e: Exception) {
            logger.error("❌ data-engine 상태 조회 실패: ${e.message}", e)
            PackageStatusResponse(
                service = "ml-package-manager",
                status = "error",
                message = "data-engine 상태 조회 실패: ${e.message}",
                timestamp = java.time.LocalDateTime.now().toString()
            )
        }
    }
}
