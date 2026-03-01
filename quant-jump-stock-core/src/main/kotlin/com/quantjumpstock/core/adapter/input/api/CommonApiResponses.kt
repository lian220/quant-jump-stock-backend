package com.quantjumpstock.core.adapter.input.api

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses

/**
 * 공통 API 응답 어노테이션 정의
 * 재사용 가능한 응답 패턴을 메타 어노테이션으로 제공
 */

/**
 * 표준 성공 응답 (200 OK)
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "200",
    description = "요청 성공"
)
annotation class SuccessResponse

/**
 * 표준 에러 응답 (500 Internal Server Error)
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "500",
    description = "서버 오류"
)
annotation class ErrorResponse

/**
 * 표준 응답 세트 (200 OK + 500 Error)
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponses(
    value = [
        ApiResponse(responseCode = "200", description = "요청 성공"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    ]
)
annotation class StandardApiResponses

/**
 * ML 패키지 업로드 성공 응답
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponses(
    value = [
        ApiResponse(
            responseCode = "200",
            description = "패키지 업로드 성공",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    value = """{
  "success": true,
  "message": "패키지 업로드 완료",
  "gcs_uri": "gs://bucket/package/v1/predict_optimized.tar.gz",
  "version": "1"
}"""
                )]
            )]
        ),
        ApiResponse(responseCode = "500", description = "업로드 실패")
    ]
)
annotation class PackageUploadResponses

/**
 * 예측 결과 조회 성공 응답
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponses(
    value = [
        ApiResponse(
            responseCode = "200",
            description = "예측 결과 조회 성공",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    value = """{
  "success": true,
  "count": 10,
  "predictions": [...]
}"""
                )]
            )]
        ),
        ApiResponse(responseCode = "500", description = "조회 실패")
    ]
)
annotation class PredictionQueryResponses

/**
 * Vertex AI Job 실행 성공 응답
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponses(
    value = [
        ApiResponse(
            responseCode = "200",
            description = "Job 실행 성공",
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    value = """{
  "success": true,
  "message": "Vertex AI 예측 실행 완료",
  "jobId": "projects/123/locations/us-central1/customJobs/456",
  "estimatedTime": "3-5분"
}"""
                )]
            )]
        ),
        ApiResponse(responseCode = "500", description = "실행 실패")
    ]
)
annotation class VertexAIJobResponses

