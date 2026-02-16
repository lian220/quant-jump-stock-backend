package com.quantjumpstock.core.application.news

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "뉴스 카테고리")
data class CategoryDto(
    @Schema(description = "카테고리 ID") val id: Long,
    @Schema(description = "카테고리명 (한국어)", example = "실적발표") val name: String,
    @Schema(description = "카테고리명 (영어)", example = "Earnings") val nameEn: String,
    @Schema(description = "카테고리 그룹", example = "COMPANY") val group: String,
    @Schema(description = "설명") val description: String?,
    @Schema(description = "아이콘 (Lucide)", example = "trending-up") val icon: String?,
    @Schema(description = "가중치 (0.0~1.0)", example = "0.40") val weight: Double
)

@Schema(description = "카테고리 그룹")
data class CategoryGroupDto(
    @Schema(description = "그룹 코드", example = "COMPANY") val group: String,
    @Schema(description = "그룹 표시명", example = "기업") val groupLabel: String,
    @Schema(description = "카테고리 목록") val categories: List<CategoryDto>
)

@Schema(description = "카테고리 목록 응답")
data class CategoryListResponse(
    @Schema(description = "그룹별 카테고리") val groups: List<CategoryGroupDto>,
    @Schema(description = "총 카테고리 수") val total: Int
)
