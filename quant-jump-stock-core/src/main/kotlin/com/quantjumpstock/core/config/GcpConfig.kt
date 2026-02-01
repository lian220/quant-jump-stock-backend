package com.quantjumpstock.core.config

import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.aiplatform.v1.JobServiceClient
import com.google.cloud.aiplatform.v1.JobServiceSettings
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import java.io.FileInputStream

/**
 * Google Cloud Platform 설정
 * gcp.enabled=true일 때만 활성화
 */
@Configuration
@EnableAsync
@ConditionalOnProperty(name = ["gcp.enabled"], havingValue = "true", matchIfMissing = false)
class GcpConfig(
    @Value("\${gcp.project-id}") private val projectId: String,
    @Value("\${gcp.region}") private val region: String,
    @Value("\${gcp.credentials-path:}") private val credentialsPath: String?
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        logger.info("=".repeat(60))
        logger.info("GCP Configuration Initialized")
        logger.info("Project ID: $projectId")
        logger.info("Region: $region")
        logger.info("Credentials Path: ${credentialsPath ?: "Application Default Credentials (ADC)"}")
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

    /**
     * Vertex AI JobServiceClient
     */
    @Bean
    fun jobServiceClient(credentials: GoogleCredentials): JobServiceClient {
        val endpoint = "$region-aiplatform.googleapis.com:443"

        logger.info("=" .repeat(60))
        logger.info("Creating Vertex AI JobServiceClient")
        logger.info("Project: $projectId")
        logger.info("Region: $region")
        logger.info("Endpoint: $endpoint")
        logger.info("=" .repeat(60))

        // Credentials를 refresh하여 최신 access token 확보
        try {
            credentials.refresh()
            logger.info("✅ Credentials refreshed successfully")
        } catch (e: Exception) {
            logger.error("❌ Failed to refresh credentials", e)
            throw e
        }

        val settings = JobServiceSettings.newBuilder()
            .setEndpoint(endpoint)
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
            .build()

        logger.info("✅ JobServiceClient created successfully")
        return JobServiceClient.create(settings)
    }
}
