package com.quantjumpstock.core.application.category

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 카테고리 응답 DTO
 */
data class CategoryDto(
    @Schema(description = "카테고리 ID")
    val id: Long,

    @Schema(description = "카테고리 코드", example = "MOMENTUM")
    val code: String,

    @Schema(description = "카테고리 이름", example = "모멘텀")
    val name: String,

    @Schema(description = "카테고리 설명")
    val description: String?,

    @Schema(description = "아이콘", example = "trending-up")
    val icon: String?,

    @Schema(description = "정렬 순서")
    val sortOrder: Int
)

/**
 * 카테고리 목록 응답
 */
data class CategoriesResponse(
    @Schema(description = "카테고리 목록")
    val categories: List<CategoryDto>,

    @Schema(description = "총 개수")
    val total: Int
)
