package com.quantjumpstock.core.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import com.quantjumpstock.core.adapter.output.gcp.GcpProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

/**
 * Google Cloud Platform 설정
 * gcp.enabled=true일 때만 활성화
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(GcpProperties::class)
@ConditionalOnProperty(name = ["gcp.enabled"], havingValue = "true", matchIfMissing = false)
class GcpConfig(
    private val gcpProperties: GcpProperties
) {
    private val projectId: String get() = gcpProperties.projectId
    private val region: String get() = gcpProperties.region
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        logger.info("=".repeat(60))
        logger.info("GCP Configuration Initialized")
        logger.info("Project ID: $projectId")
        logger.info("Region: $region")
        logger.info("Credentials Path: ${gcpProperties.credentialsPath ?: "Application Default Credentials (ADC)"}")
        logger.info("=".repeat(60))
    }

    /**
     * Google Cloud Storage Client
     * 크레덴셜을 직접 로드하여 Storage 클라이언트를 생성합니다.
     */
    @Bean
    fun storageClient(): Storage {
        val credPath = gcpProperties.credentialsPath
        logger.info("Loading Vertex AI credentials from: ${credPath ?: "ADC"}")

        val credentials = try {
            if (!credPath.isNullOrBlank()) {
                java.io.FileInputStream(credPath).use { stream ->
                    GoogleCredentials.fromStream(stream)
                }
            } else {
                GoogleCredentials.getApplicationDefault()
            }
        } catch (e: Exception) {
            logger.error("Failed to load GCP credentials: ${e.message}", e)
            throw e
        }

        val scoped = credentials.createScoped(
            listOf("https://www.googleapis.com/auth/cloud-platform")
        )
        logger.info("✅ Storage client created for project: $projectId")

        return StorageOptions.newBuilder()
            .setProjectId(projectId)
            .setCredentials(scoped)
            .build()
            .service
    }

}
