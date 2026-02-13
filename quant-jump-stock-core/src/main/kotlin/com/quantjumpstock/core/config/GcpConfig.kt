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
     * Google Cloud Credentials
     */
    @Bean
    fun googleCredentials(): GoogleCredentials {
        logger.info("Loading Google Cloud Credentials...")
        logger.info("GOOGLE_APPLICATION_CREDENTIALS env: ${System.getenv("GOOGLE_APPLICATION_CREDENTIALS")}")

        // Application Default Credentials 사용 (GOOGLE_APPLICATION_CREDENTIALS 환경변수 활용)
        var credentials = GoogleCredentials.getApplicationDefault()

        // Vertex AI API 사용을 위한 스코프 설정 (무조건 적용)
        // ServiceAccountCredentials는 항상 scoping이 필요
        credentials = credentials.createScoped(
            listOf("https://www.googleapis.com/auth/cloud-platform")
        )
        logger.info("✅ Credentials scoped for Vertex AI (cloud-platform)")

        logger.info("✅ Credentials loaded successfully")
        logger.info("Credentials type: ${credentials.javaClass.simpleName}")

        return credentials
    }

    /**
     * Google Cloud Storage Client
     */
    @Bean
    fun storageClient(credentials: GoogleCredentials): Storage {
        return StorageOptions.newBuilder()
            .setProjectId(projectId)
            .setCredentials(credentials)
            .build()
            .service
    }

}
