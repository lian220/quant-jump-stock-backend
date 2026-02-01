package com.quantjumpstock.core.adapter.input.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping

/**
 * ML 패키지 관리 API 스펙
 */
@Tag(name = "ML Package", description = "ML 패키지 GCS 업로드 관리")
interface MlPackageApi {

    @PostMapping("/upload-package")
    @Operation(
        summary = "ML 패키지 GCS 업로드",
        description = "predict_optimized.py를 Vertex AI 패키지로 빌드하고 GCS에 업로드합니다."
    )
    @PackageUploadResponses
    fun uploadPackage(): ResponseEntity<Map<String, Any>>

    @GetMapping("/package-status")
    @Operation(
        summary = "패키지 상태 조회",
        description = "현재 GCS에 업로드된 ML 패키지 상태를 조회합니다."
    )
    @StandardApiResponses
    fun getPackageStatus(): ResponseEntity<Map<String, Any>>
}
