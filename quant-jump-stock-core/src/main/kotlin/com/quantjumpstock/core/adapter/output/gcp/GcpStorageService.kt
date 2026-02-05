package com.quantjumpstock.core.adapter.output.gcp

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import com.quantjumpstock.core.domain.ml.port.output.MlPackageStoragePort
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * GCS(Google Cloud Storage) 통합 서비스 (Output Adapter)
 *
 * MlPackageStoragePort를 구현하여 GCS에 접근.
 * ML 패키지 업로드, 버전 관리, 상태 조회 등 GCS 관련 기능 담당.
 *
 * ML 스크립트 위치: backend/scripts/ml/
 * - predict_optimized.py: 주식 예측 ML 스크립트
 * - (미래) strategy_executor.py: 투자 전략 실행기
 */
@Service
@ConditionalOnProperty(name = ["gcp.enabled"], havingValue = "true")
class GcpStorageService(
    private val storage: Storage,
    private val resourceLoader: ResourceLoader,
    private val gcpProperties: GcpProperties,
    @Value("\${ml.scripts.base-path:#{null}}")
    private val scriptsBasePath: String?
) : MlPackageStoragePort {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val bucketName: String
        get() = gcpProperties.vertexAi.modelBucket

    private val packageBasePath: String
        get() = gcpProperties.vertexAi.packageBasePath

    companion object {
        private const val PACKAGE_PREFIX = "quantiq-ml-package"
        // Vertex AI PythonPackageSpec에서 사용하는 모듈명과 일치해야 함
        private const val TRAINER_DIR = "aiplatform_custom_trainer_script"
    }

    /**
     * ML 패키지를 GCS에 업로드
     *
     * @param scriptName 업로드할 스크립트 이름 (기본: predict_optimized.py)
     * @return 업로드 결과
     */
    override fun uploadPackage(scriptName: String): MlPackageStoragePort.UploadResult {
        logger.info("=".repeat(60))
        logger.info("📦 GCS ML 패키지 업로드 시작")
        logger.info("버킷: $bucketName")
        logger.info("기본 경로: $packageBasePath")
        logger.info("스크립트: $scriptName")
        logger.info("=".repeat(60))

        return try {
            val currentVersion = getCurrentVersion()
            val newVersion = currentVersion + 1

            logger.info("현재 버전: v$currentVersion → 새 버전: v$newVersion")

            // 스크립트 로드: 파일시스템 우선, 없으면 classpath
            val pythonScript = loadScript(scriptName)
            val setupScript = loadSetupScript()
            val packageBytes = createTarGzPackage(pythonScript, setupScript)

            // 단일 폴더에 버전별 파일명으로 저장 (v1/, v2/ 폴더 생성 안함)
            val blobPath = "$packageBasePath/$PACKAGE_PREFIX-v$newVersion.tar.gz"
            val blobId = BlobId.of(bucketName, blobPath)
            val blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType("application/gzip")
                .build()

            storage.create(blobInfo, packageBytes)

            val gcsUri = "gs://$bucketName/$blobPath"

            logger.info("=".repeat(60))
            logger.info("✅ GCS 패키지 업로드 완료")
            logger.info("GCS URI: $gcsUri")
            logger.info("버전: v$newVersion")
            logger.info("크기: ${packageBytes.size} bytes")
            logger.info("=".repeat(60))

            MlPackageStoragePort.UploadResult(
                success = true,
                message = "패키지 업로드 완료",
                gcsUri = gcsUri,
                version = newVersion
            )

        } catch (e: Exception) {
            logger.error("❌ GCS 패키지 업로드 실패", e)
            MlPackageStoragePort.UploadResult(
                success = false,
                message = "패키지 업로드 실패: ${e.message}"
            )
        }
    }

    /**
     * 현재 GCS에 업로드된 최신 버전 조회
     */
    fun getCurrentVersion(): Int {
        return try {
            val blobs = storage.list(
                bucketName,
                Storage.BlobListOption.prefix(packageBasePath)
            )

            var maxVersion = 0
            blobs.iterateAll().forEach { blob ->
                val versionMatch = Regex("v(\\d+)").find(blob.name)
                versionMatch?.groupValues?.get(1)?.toIntOrNull()?.let { version ->
                    if (version > maxVersion) {
                        maxVersion = version
                    }
                }
            }

            logger.info("현재 GCS 최신 버전: v$maxVersion")
            maxVersion

        } catch (e: Exception) {
            logger.warn("버전 조회 실패, 0으로 시작: ${e.message}")
            0
        }
    }

    /**
     * 패키지 상태 조회
     */
    override fun getPackageStatus(): Map<String, Any> {
        return try {
            val currentVersion = getCurrentVersion()
            val latestBlobPath = if (currentVersion > 0) {
                "$packageBasePath/$PACKAGE_PREFIX-v$currentVersion.tar.gz"
            } else {
                null
            }

            val latestBlob = latestBlobPath?.let {
                storage.get(BlobId.of(bucketName, it))
            }

            mapOf(
                "bucket" to bucketName,
                "basePath" to packageBasePath,
                "currentVersion" to currentVersion,
                "latestPackage" to (latestBlob?.let {
                    mapOf(
                        "gcsUri" to "gs://$bucketName/${it.name}",
                        "size" to it.size,
                        "created" to it.createTimeOffsetDateTime?.toString(),
                        "updated" to it.updateTimeOffsetDateTime?.toString()
                    )
                } ?: "없음"),
                "timestamp" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        } catch (e: Exception) {
            logger.error("패키지 상태 조회 실패", e)
            mapOf(
                "error" to (e.message ?: "알 수 없는 오류"),
                "timestamp" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        }
    }

    /**
     * 스토리지 사용 가능 여부
     */
    override fun isAvailable(): Boolean = true

    /**
     * 버킷 존재 확인
     */
    fun ensureBucketExists(bucketName: String) {
        val bucket = storage.get(bucketName)
        if (bucket == null) {
            logger.warn("버킷이 없습니다. 수동으로 생성해주세요: $bucketName")
            throw IllegalStateException("GCS 버킷이 존재하지 않습니다: $bucketName")
        }
        logger.info("✅ 버킷 확인 완료: $bucketName")
    }

    /**
     * 최신 패키지 URI 조회
     * uploadPackage()로 업로드된 ml-packages 경로에서 최신 버전 조회
     */
    fun resolveLatestPackageUri(): String {
        // 명시적 URI가 설정되어 있으면 우선 사용
        val explicitUri = gcpProperties.vertexAi.packageUri
        if (!explicitUri.isNullOrBlank()) {
            return explicitUri
        }

        // 현재 버전 조회 (uploadPackage와 동일한 경로 사용)
        val currentVersion = getCurrentVersion()
        if (currentVersion == 0) {
            throw IllegalStateException("GCS에 업로드된 패키지가 없습니다. /api/v1/ml-packages/upload-package를 먼저 실행하세요.")
        }

        val packagePath = "$packageBasePath/$PACKAGE_PREFIX-v$currentVersion.tar.gz"
        val gcsUri = "gs://$bucketName/$packagePath"

        logger.info("✅ 최신 버전 패키지 발견: v$currentVersion")
        logger.info("   URI: $gcsUri")

        return gcsUri
    }

    /**
     * tar.gz 패키지 생성
     */
    private fun createTarGzPackage(pythonScript: String, setupScript: String): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()

        GzipCompressorOutputStream(byteArrayOutputStream).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)

                addDirectoryEntry(tar, "$TRAINER_DIR/")
                addFileEntry(tar, "$TRAINER_DIR/__init__.py", "")
                addFileEntry(tar, "$TRAINER_DIR/task.py", pythonScript)
                addFileEntry(tar, "setup.py", setupScript)

                tar.finish()
            }
        }

        return byteArrayOutputStream.toByteArray()
    }

    private fun addDirectoryEntry(tar: TarArchiveOutputStream, name: String) {
        val entry = TarArchiveEntry(name)
        entry.mode = 493 // 0755
        tar.putArchiveEntry(entry)
        tar.closeArchiveEntry()
    }

    private fun addFileEntry(tar: TarArchiveOutputStream, name: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val entry = TarArchiveEntry(name)
        entry.size = bytes.size.toLong()
        entry.mode = 420 // 0644
        tar.putArchiveEntry(entry)
        tar.write(bytes)
        tar.closeArchiveEntry()
    }

    /**
     * ML 스크립트 로드
     * 파일시스템(scripts/ml/) 우선, 없으면 classpath에서 로드
     */
    private fun loadScript(scriptName: String): String {
        // 1. 파일시스템에서 로드 시도 (backend/scripts/ml/)
        if (!scriptsBasePath.isNullOrBlank()) {
            val scriptFile = File(scriptsBasePath, "ml/$scriptName")
            if (scriptFile.exists()) {
                logger.info("📄 스크립트 로드 (파일시스템): ${scriptFile.absolutePath}")
                return scriptFile.readText(Charsets.UTF_8)
            }
        }

        // 2. Docker 환경: /scripts/ml/ 에서 로드 시도
        val dockerPath = File("/scripts/ml/$scriptName")
        if (dockerPath.exists()) {
            logger.info("📄 스크립트 로드 (Docker): ${dockerPath.absolutePath}")
            return dockerPath.readText(Charsets.UTF_8)
        }

        // 3. Classpath에서 로드 (폴백)
        logger.info("📄 스크립트 로드 (classpath): ml/$scriptName")
        return loadTemplate("ml/$scriptName")
    }

    /**
     * setup.py 로드
     */
    private fun loadSetupScript(): String {
        // 파일시스템에서 setup.py 로드 시도
        if (!scriptsBasePath.isNullOrBlank()) {
            val setupFile = File(scriptsBasePath, "ml/setup.py")
            if (setupFile.exists()) {
                return setupFile.readText(Charsets.UTF_8)
            }
        }

        val dockerSetup = File("/scripts/ml/setup.py")
        if (dockerSetup.exists()) {
            return dockerSetup.readText(Charsets.UTF_8)
        }

        // 기본 setup.py 생성
        return generateDefaultSetup()
    }

    /**
     * 기본 setup.py 생성
     */
    private fun generateDefaultSetup(): String {
        return """
from setuptools import setup, find_packages

setup(
    name="aiplatform-custom-trainer-script",
    version="1.0.0",
    packages=find_packages(),
    install_requires=[
        "numpy>=1.21.0",
        "pandas>=1.3.0",
        "pymongo>=3.12.0",
        "xgboost>=1.5.0",
        "scikit-learn>=1.0.0",
        "google-cloud-storage>=1.44.0",
        "psycopg2-binary>=2.9.0",
        "requests>=2.28.0",
    ],
    python_requires=">=3.8",
)
        """.trimIndent()
    }

    /**
     * 리소스에서 템플릿 로드 (폴백용)
     */
    private fun loadTemplate(resourcePath: String): String {
        return try {
            val resource = resourceLoader.getResource("classpath:$resourcePath")
            if (resource.exists()) {
                resource.inputStream.bufferedReader().use { it.readText() }
            } else {
                throw IllegalStateException("템플릿 파일을 찾을 수 없습니다: $resourcePath")
            }
        } catch (e: Exception) {
            logger.error("템플릿 로드 실패: $resourcePath", e)
            throw e
        }
    }
}
