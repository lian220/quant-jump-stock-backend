package com.quantjumpstock.core.application.common

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "공통 응답")
data class SimpleResponse(
    val success: Boolean,
    val message: String? = null
)
