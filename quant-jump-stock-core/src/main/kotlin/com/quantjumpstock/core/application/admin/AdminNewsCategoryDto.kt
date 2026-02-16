package com.quantjumpstock.core.application.admin

data class AdminCategoryDto(
    val id: Long,
    val name: String,
    val nameEn: String,
    val group: String,
    val description: String?,
    val icon: String?,
    val weight: Double,
    val isActive: Boolean,
    val sortOrder: Int,
    val createdAt: String?
)

data class AdminCategoryListResponse(
    val categories: List<AdminCategoryDto>,
    val total: Int
)

data class CreateCategoryRequest(
    val name: String,
    val nameEn: String,
    val group: String,
    val description: String? = null,
    val icon: String? = null,
    val weight: Double = 0.10,
    val sortOrder: Int = 0
)

data class UpdateCategoryRequest(
    val name: String? = null,
    val nameEn: String? = null,
    val group: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val weight: Double? = null,
    val sortOrder: Int? = null,
    val isActive: Boolean? = null
)
