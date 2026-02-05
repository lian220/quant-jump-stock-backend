package com.quantjumpstock.core.adapter.input.rest.ml

import com.quantjumpstock.core.adapter.input.api.MlPackageApi
import com.quantjumpstock.core.application.ml.MlPackageService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * ML 패키지 관리 Controller (Input Adapter)
 *
 * HTTP 요청을 MlPackageService로 전달하는 Adapter 역할만 수행.
 * 비즈니스 로직은 Application Service에서 처리.
 */
@RestController
@RequestMapping("/api/v1/ml-packages")
class MlPackageController(
    private val mlPackageService: MlPackageService
) : MlPackageApi {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun uploadPackage(): ResponseEntity<Map<String, Any>> {
        return try {
            val result = mlPackageService.uploadPackage()

            if (!result.available) {
                // GCP 비활성화 상태
                ResponseEntity.status(503).body(
                    mapOf(
                        "success" to false,
                        "message" to result.message,
                        "timestamp" to result.timestamp
                    )
                )
            } else if (result.success) {
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
                    "message" to "패키지 업로드 요청 실패: ${e.message}"
                )
            )
        }
    }

    override fun getPackageStatus(): ResponseEntity<Map<String, Any>> {
        return try {
            val result = mlPackageService.getPackageStatus()

            if (!result.available) {
                // GCP 비활성화 상태
                ResponseEntity.ok(
                    mapOf(
                        "service" to result.service,
                        "status" to result.status,
                        "message" to (result.message ?: ""),
                        "timestamp" to result.timestamp
                    )
                )
            } else {
                ResponseEntity.ok(result.packageInfo ?: emptyMap())
            }
        } catch (e: Exception) {
            logger.error("❌ 패키지 상태 조회 실패", e)
            ResponseEntity.status(500).body(
                mapOf(
                    "success" to false,
                    "message" to "패키지 상태 조회 실패: ${e.message}"
                )
            )
        }
    }
}
