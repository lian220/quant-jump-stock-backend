package com.quantjumpstock.core.adapter.input.rest.ml

import com.quantjumpstock.core.adapter.output.external.DataEngineClient
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * ML 패키지 관리 Controller
 *
 * Core API에서 요청을 받아 Data Engine으로 위임합니다.
 * 실제 GCS 업로드/상태 조회는 Data Engine(Python)에서 처리.
 */
@RestController
@RequestMapping("/api/v1/ml-packages")
class MlPackageController(
    private val dataEngineClient: DataEngineClient
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * ML 패키지를 GCS에 업로드
     * Core API → Data Engine → GCS
     */
    @PostMapping("/upload-package")
    fun uploadPackage(): ResponseEntity<Map<String, Any>> {
        logger.info("📦 ML 패키지 업로드 요청 → Data Engine 위임")

        return try {
            val result = dataEngineClient.uploadMlPackage()

            if (result.success) {
                ResponseEntity.ok(
                    mapOf(
                        "success" to true,
                        "message" to result.message,
                        "gcs_uri" to (result.gcs_uri ?: ""),
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
            logger.error("패키지 업로드 요청 실패", e)
            ResponseEntity.status(500).body(
                mapOf(
                    "success" to false,
                    "message" to "패키지 업로드 요청 실패: ${e.message}"
                )
            )
        }
    }

    /**
     * ML 패키지 상태 조회
     * Core API → Data Engine
     */
    @GetMapping("/status")
    fun getPackageStatus(): ResponseEntity<Map<String, Any>> {
        logger.info("📋 ML 패키지 상태 조회 → Data Engine 위임")

        return try {
            val result = dataEngineClient.getPackageStatus()
            val responseMap = mutableMapOf<String, Any>(
                "bucket" to result.bucket,
                "base_path" to result.base_path,
                "current_version" to result.current_version,
                "timestamp" to result.timestamp
            )
            result.latest_package?.let { pkg ->
                responseMap["latest_package"] = mapOf(
                    "gcs_uri" to (pkg.gcs_uri ?: ""),
                    "size" to (pkg.size ?: 0),
                    "updated" to (pkg.updated ?: "")
                )
            }
            ResponseEntity.ok(responseMap as Map<String, Any>)
        } catch (e: Exception) {
            logger.error("패키지 상태 조회 실패", e)
            ResponseEntity.status(500).body(
                mapOf(
                    "success" to false,
                    "message" to "패키지 상태 조회 실패: ${e.message}"
                )
            )
        }
    }
}
