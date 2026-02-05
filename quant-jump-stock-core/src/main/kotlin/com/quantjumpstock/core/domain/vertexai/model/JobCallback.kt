package com.quantjumpstock.core.domain.vertexai.model

/**
 * Vertex AI Job 콜백 도메인 모델
 *
 * predict_optimized.py에서 작업 완료 시 전송하는 콜백 데이터.
 */
data class JobCallback(
    val requestId: String,
    val status: JobStatus,
    val message: String? = null,
    val threadTs: String? = null,
    val duration: String? = null,
    val predictedCount: Int? = null,
    val errorDetail: String? = null
)

/**
 * Job 상태 열거형
 */
enum class JobStatus {
    SUCCESS,
    FAILED;

    companion object {
        fun fromString(value: String): JobStatus {
            return when (value.uppercase()) {
                "SUCCESS" -> SUCCESS
                "FAILED" -> FAILED
                else -> throw IllegalArgumentException("Unknown job status: $value")
            }
        }
    }
}
