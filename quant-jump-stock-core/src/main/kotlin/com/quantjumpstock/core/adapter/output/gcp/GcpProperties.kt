package com.quantjumpstock.core.adapter.output.gcp

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * GCP 설정 프로퍼티
 * application.yml의 gcp.* 설정을 바인딩
 * 기본값은 application.yml에서 관리, 실제 값은 .env.prod 환경변수에서 로드
 */
@ConfigurationProperties(prefix = "gcp")
data class GcpProperties(
    val enabled: Boolean,
    val projectId: String,
    val region: String,
    val credentialsPath: String?,
    val vertexAi: VertexAiProperties
) {
    data class VertexAiProperties(
        val stagingBucket: String,
        val modelBucket: String,
        val packageBasePath: String,
        val packageUri: String?,
        val jobName: String,
        val machineType: String,
        val acceleratorType: String,
        val acceleratorCount: Int,
        val maxWaitDuration: Int,
        val timeout: Int,
        val containerUri: String
    )
}
