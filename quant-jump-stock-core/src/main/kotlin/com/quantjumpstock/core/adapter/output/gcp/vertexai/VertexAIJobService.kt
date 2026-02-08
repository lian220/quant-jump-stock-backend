package com.quantjumpstock.core.adapter.output.gcp.vertexai

import com.google.cloud.aiplatform.v1.*
import com.quantjumpstock.core.adapter.output.gcp.GcpProperties
import com.quantjumpstock.core.adapter.output.gcp.GcpStorageService
import com.quantjumpstock.core.adapter.output.notification.slack.SlackApiClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Vertex AI CustomJob 관리 서비스
 * GCS에 업로드된 패키지를 사용하여 Vertex AI CustomJob 실행
 */
@Service
@ConditionalOnProperty(name = ["gcp.enabled"], havingValue = "true", matchIfMissing = false)
class VertexAIJobService(
    private val jobServiceClient: JobServiceClient,
    private val gcpStorageService: GcpStorageService,
    private val slackApiClient: SlackApiClient,
    private val gcpProperties: GcpProperties,
    private val environment: Environment
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Vertex AI CustomJob 생성 및 실행
     *
     * @return Job 이름 (full resource name)
     */
    fun createAndRunCustomJob(): String {
        val requestId = UUID.randomUUID().toString()

        logger.info("=".repeat(60))
        logger.info("🚀 Vertex AI 주가 예측 작업 시작")
        logger.info("Request ID: $requestId")
        logger.info("Project: ${gcpProperties.projectId}")
        logger.info("Region: ${gcpProperties.region}")
        logger.info("=".repeat(60))

        try {
            val threadTs = slackApiClient.notifyVertexAIJobStarted(requestId, gcpProperties.vertexAi.jobName)
            val resolvedPackageUri = gcpStorageService.resolveLatestPackageUri()

            logger.info("패키지 URI: $resolvedPackageUri")

            val envVars = buildEnvironmentVariables(threadTs, requestId)
            val customJob = buildCustomJob(resolvedPackageUri, envVars)

            val parent = LocationName.of(gcpProperties.projectId, gcpProperties.region).toString()
            val createdJob = jobServiceClient.createCustomJob(parent, customJob)
            val fullJobName = createdJob.name

            logger.info("=".repeat(60))
            logger.info("✅ Vertex AI CustomJob 생성 완료")
            logger.info("Job Name: $fullJobName")
            logger.info("Job State: ${createdJob.state}")
            logger.info("Slack ThreadTs: $threadTs")
            logger.info("Console: https://console.cloud.google.com/vertex-ai/training/custom-jobs/${fullJobName}?project=${gcpProperties.projectId}")
            logger.info("=".repeat(60))

            return fullJobName

        } catch (e: Exception) {
            logger.error("❌ Vertex AI CustomJob 실행 실패", e)
            slackApiClient.notifyVertexAIJobFailed(
                requestId,
                gcpProperties.vertexAi.jobName,
                e.message ?: "Unknown error",
                null
            )
            throw RuntimeException("Vertex AI CustomJob 실행 실패", e)
        }
    }

    /**
     * Job 상태 조회
     */
    fun getJobState(jobId: String): JobState {
        val job = jobServiceClient.getCustomJob(jobId)
        return job.state
    }

    /**
     * Job 취소
     */
    fun cancelJob(jobId: String) {
        jobServiceClient.cancelCustomJob(jobId)
        logger.info("Job 취소 요청 완료: $jobId")
    }

    /**
     * 환경 변수 빌드
     * Spring Environment에서 설정 로드 (Profile 기반)
     */
    private fun buildEnvironmentVariables(threadTs: String?, requestId: String): Map<String, String> {
        val envVars = mutableMapOf(
            "GCS_BUCKET" to gcpProperties.vertexAi.modelBucket,
            "GCP_PROJECT_ID" to gcpProperties.projectId,
            "FINE_TUNE_MODE" to "true",
            "FINE_TUNE_EPOCHS" to "5",
            "FULL_TRAIN_EPOCHS" to "50",
            "REQUEST_ID" to requestId
        )

        threadTs?.let {
            envVars["SLACK_THREAD_TS"] = it
            logger.info("SLACK_THREAD_TS 환경변수 추가됨: $it")
        }

        // MongoDB 환경변수 - MONGODB_URI 하나로 통일
        environment.getProperty("MONGODB_URI")?.let { envVars["VERTEX_AI_MONGODB_URI"] = it }
        val mongoDbName = environment.getProperty("MONGODB_DB_NAME") ?: "stock_trading"
        envVars["VERTEX_AI_MONGODB_DATABASE"] = mongoDbName

        // PostgreSQL 환경변수 (stocks, indicators 조회용)
        // Vertex AI는 GCP에서 실행되므로 외부 접근 가능한 DB 설정 필요
        // VERTEX_AI_DB_* 환경변수 우선, 없으면 기본 DB_* 사용
        val dbHost = environment.getProperty("VERTEX_AI_DB_HOST")
            ?: environment.getProperty("DB_HOST")
        val dbPort = environment.getProperty("VERTEX_AI_DB_PORT")
            ?: environment.getProperty("DB_PORT")
            ?: "5432"
        val dbName = environment.getProperty("VERTEX_AI_DB_NAME")
            ?: environment.getProperty("DB_NAME")
        val dbUser = environment.getProperty("VERTEX_AI_DB_USER")
            ?: environment.getProperty("DB_USER")
        val dbPassword = environment.getProperty("VERTEX_AI_DB_PASSWORD")
            ?: environment.getProperty("DB_PASSWORD")

        // Docker 내부 호스트명(postgresql, localhost)은 Vertex AI에서 접근 불가
        val invalidHosts = setOf("postgresql", "localhost", "127.0.0.1")
        if (dbHost != null && dbHost !in invalidHosts) {
            envVars["DB_HOST"] = dbHost
            envVars["DB_PORT"] = dbPort
            dbName?.let { envVars["DB_NAME"] = it }
            dbUser?.let { envVars["DB_USER"] = it }
            dbPassword?.let { envVars["DB_PASSWORD"] = it }
            logger.info("✅ PostgreSQL 환경변수 설정: $dbHost:$dbPort/$dbName")
        } else {
            logger.warn("⚠️ Vertex AI용 PostgreSQL 설정 없음 (VERTEX_AI_DB_HOST 또는 외부 접근 가능한 DB_HOST 필요)")
            logger.warn("   현재 DB_HOST: $dbHost (Docker 내부 호스트명은 Vertex AI에서 접근 불가)")
        }

        // Slack 환경변수 (predict_optimized.py에서 직접 Slack 알림 전송)
        environment.getProperty("SLACK_BOT_TOKEN")?.let { envVars["SLACK_BOT_TOKEN"] = it }
        environment.getProperty("SLACK_CHANNEL")?.let { envVars["SLACK_CHANNEL"] = it }

        logger.info("📋 환경 변수 ${envVars.size}개 설정 완료")
        logEnvVarsSafely(envVars)

        return envVars
    }

    private fun logEnvVarsSafely(envVars: Map<String, String>) {
        val sensitiveKeys = setOf("PASSWORD", "SECRET", "TOKEN", "KEY")
        envVars.forEach { (key, value) ->
            val maskedValue = if (sensitiveKeys.any { key.uppercase().contains(it) }) "***" else value
            logger.info("  $key: $maskedValue")
        }
    }

    /**
     * CustomJob 빌드
     */
    private fun buildCustomJob(packageUri: String, envVars: Map<String, String>): CustomJob {
        val vertexAiProps = gcpProperties.vertexAi

        // 빈 값 필터링 (Vertex AI는 빈 값을 허용하지 않음)
        val filteredEnvVars = envVars.filterValues { it.isNotBlank() }
        logger.info("📋 환경 변수 필터링: ${envVars.size}개 → ${filteredEnvVars.size}개 (빈 값 ${envVars.size - filteredEnvVars.size}개 제거)")

        val envVarList = filteredEnvVars.map { (key, value) ->
            EnvVar.newBuilder()
                .setName(key)
                .setValue(value)
                .build()
        }

        val pythonPackageSpec = PythonPackageSpec.newBuilder()
            .setExecutorImageUri(vertexAiProps.containerUri)
            .addPackageUris(packageUri)
            .setPythonModule("aiplatform_custom_trainer_script.task")
            .addAllEnv(envVarList)
            .build()

        val machineSpec = MachineSpec.newBuilder()
            .setMachineType(vertexAiProps.machineType)
            .setAcceleratorType(AcceleratorType.valueOf(vertexAiProps.acceleratorType))
            .setAcceleratorCount(vertexAiProps.acceleratorCount)
            .build()

        val workerPoolSpec = WorkerPoolSpec.newBuilder()
            .setMachineSpec(machineSpec)
            .setReplicaCount(1)
            .setPythonPackageSpec(pythonPackageSpec)
            .build()

        val jobSpec = CustomJobSpec.newBuilder()
            .addWorkerPoolSpecs(workerPoolSpec)
            .build()

        return CustomJob.newBuilder()
            .setDisplayName(vertexAiProps.jobName)
            .setJobSpec(jobSpec)
            .build()
    }
}
