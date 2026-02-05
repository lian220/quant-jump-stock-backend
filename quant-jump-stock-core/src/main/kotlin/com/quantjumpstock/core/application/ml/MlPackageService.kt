package com.quantjumpstock.core.application.ml

import com.quantjumpstock.core.domain.ml.port.output.MlPackageStoragePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * ML Package Application Service
 *
 * ML 패키지 관리 비즈니스 로직을 담당.
 * GCP가 비활성화된 경우에도 동작하도록 nullable로 의존성 주입.
 */
@Service
class MlPackageService(
    private val mlPackageStoragePort: MlPackageStoragePort?
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 스토리지 사용 가능 상태 확인
     *
     * @return 스토리지 사용 가능 여부
     */
    fun isStorageAvailable(): Boolean {
        return mlPackageStoragePort?.isAvailable() ?: false
    }

    /**
     * ML 패키지 업로드
     *
     * @param scriptName 업로드할 스크립트 이름 (기본: predict_optimized.py)
     * @return 업로드 결과
     */
    fun uploadPackage(scriptName: String = "predict_optimized.py"): MlPackageUploadResult {
        logger.info("=".repeat(60))
        logger.info("📦 ML 패키지 업로드 요청")
        logger.info("Storage Available: ${isStorageAvailable()}")
        logger.info("=".repeat(60))

        // 스토리지 비활성화 상태 체크
        if (!isStorageAvailable()) {
            logger.warn("⚠️ ML 스토리지가 비활성화 상태입니다. gcp.enabled=true로 설정해주세요.")
            return MlPackageUploadResult(
                success = false,
                available = false,
                message = "ML 스토리지가 비활성화 상태입니다. gcp.enabled=true로 설정해주세요."
            )
        }

        // 패키지 업로드 실행
        val result = mlPackageStoragePort!!.uploadPackage(scriptName)

        return if (result.success) {
            logger.info("✅ 패키지 업로드 완료")
            logger.info("Storage URI: ${result.gcsUri}")
            logger.info("버전: v${result.version}")

            MlPackageUploadResult(
                success = true,
                available = true,
                message = result.message,
                gcsUri = result.gcsUri,
                version = result.version
            )
        } else {
            logger.error("❌ 패키지 업로드 실패: ${result.message}")
            MlPackageUploadResult(
                success = false,
                available = true,
                message = result.message
            )
        }
    }

    /**
     * ML 패키지 상태 조회
     *
     * @return 패키지 상태 정보
     */
    fun getPackageStatus(): MlPackageStatusResult {
        logger.info("📋 패키지 상태 조회 요청")

        // 스토리지 비활성화 상태 체크
        if (!isStorageAvailable()) {
            return MlPackageStatusResult(
                available = false,
                service = "ml-package-manager",
                status = "disabled",
                message = "ML 스토리지가 비활성화 상태입니다"
            )
        }

        val status = mlPackageStoragePort!!.getPackageStatus()

        return MlPackageStatusResult(
            available = true,
            service = "ml-package-manager",
            status = "active",
            packageInfo = status
        )
    }
}

/**
 * ML 패키지 업로드 결과
 */
data class MlPackageUploadResult(
    val success: Boolean,
    val available: Boolean,
    val message: String,
    val gcsUri: String? = null,
    val version: Int? = null,
    val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
)

/**
 * ML 패키지 상태 결과
 */
data class MlPackageStatusResult(
    val available: Boolean,
    val service: String,
    val status: String,
    val message: String? = null,
    val packageInfo: Map<String, Any>? = null,
    val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
)
