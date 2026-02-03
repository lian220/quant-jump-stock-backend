package com.quantjumpstock.core.adapter.input.rest.ml

import com.quantjumpstock.core.adapter.input.api.MlPackageApi
import com.quantjumpstock.core.adapter.output.gcp.GcpStorageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * ML 패키지 관리 Controller
 *
 * backend/scripts/ml/ 디렉토리의 ML 스크립트를 GCS에 업로드합니다.
 * - predict_optimized.py: 주식 예측 ML 스크립트
 * - (미래) strategy_executor.py: 투자 전략 실행기
 */
@RestController
@RequestMapping("/api/v1/ml-packages")
class MlPackageController(
    private val gcpStorageService: GcpStorageService?,
    @Value("\${gcp.enabled:false}")
    private val gcpEnabled: Boolean
) : MlPackageApi {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun uploadPackage(): ResponseEntity<Map<String, Any>> {
        logger.info("=".repeat(60))
        logger.info("📦 ML 패키지 업로드 요청")
        logger.info("GCP Enabled: $gcpEnabled")
        logger.info("=".repeat(60))

        // GCP 비활성화 상태 체크
        if (!gcpEnabled || gcpStorageService == null) {
            logger.warn("⚠️ GCP가 비활성화 상태입니다. gcp.enabled=true로 설정해주세요.")
            return ResponseEntity.status(503).body(
                mapOf(
                    "success" to false,
                    "message" to "GCP가 비활성화 상태입니다. gcp.enabled=true로 설정해주세요.",
                    "timestamp" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )
            )
        }

        return try {
            // predict_optimized.py 업로드
            val result = gcpStorageService.uploadPackage("predict_optimized.py")

            if (result.success) {
                logger.info("✅ 패키지 업로드 완료")
                logger.info("GCS URI: ${result.gcsUri}")
                logger.info("버전: v${result.version}")

                ResponseEntity.ok(
                    mapOf(
                        "success" to true,
                        "message" to result.message,
                        "gcs_uri" to (result.gcsUri ?: ""),
                        "version" to (result.version ?: 0),
                        "timestamp" to result.timestamp
                    )
                )
            } else {
                logger.error("❌ 패키지 업로드 실패: ${result.message}")
                ResponseEntity.status(500).body(
                    mapOf(
                        "success" to false,
                        "message" to result.message,
                        "timestamp" to result.timestamp
                    )
                )
            }

        } catch (e: Exception) {
            logger.error("❌ 패키지 업로드 요청 실패", e)
            ResponseEntity.status(500).body(
                mapOf(
                    "success" to false,
                    "message" to "패키지 업로드 요청 실패: ${e.message}",
                    "timestamp" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )
            )
        }
    }

    override fun getPackageStatus(): ResponseEntity<Map<String, Any>> {
        logger.info("📋 패키지 상태 조회 요청")

        // GCP 비활성화 상태 체크
        if (!gcpEnabled || gcpStorageService == null) {
            return ResponseEntity.ok(
                mapOf(
                    "service" to "ml-package-manager",
                    "status" to "disabled",
                    "message" to "GCP가 비활성화 상태입니다",
                    "timestamp" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )
            )
        }

        return try {
            val status = gcpStorageService.getPackageStatus()
            ResponseEntity.ok(status)

        } catch (e: Exception) {
            logger.error("❌ 패키지 상태 조회 실패", e)
            ResponseEntity.status(500).body(
                mapOf(
                    "success" to false,
                    "message" to "패키지 상태 조회 실패: ${e.message}",
                    "timestamp" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )
            )
        }
    }
}
