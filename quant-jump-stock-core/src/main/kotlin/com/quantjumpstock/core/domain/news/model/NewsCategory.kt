package com.quantjumpstock.core.domain.news.model

import java.time.LocalDateTime

data class NewsCategory(
    val id: Long? = null,
    val name: String,
    val nameEn: String,
    val categoryGroup: String,
    val description: String? = null,
    val icon: String? = null,
    val weight: Double = 0.10,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: LocalDateTime? = null
)
