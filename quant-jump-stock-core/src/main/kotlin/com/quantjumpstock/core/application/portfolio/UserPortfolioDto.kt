package com.quantjumpstock.core.application.portfolio

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime

// ===== Request DTOs =====

data class AddPortfolioStockRequest(
    @Schema(description = "종목 ID", required = true)
    val stockId: Long,

    @Schema(description = "목표 비중 (%)", example = "20.00")
    val targetWeight: BigDecimal = BigDecimal.ZERO,

    @field:Size(max = 1000, message = "메모는 1000자를 초과할 수 없습니다")
    @Schema(description = "메모")
    val memo: String? = null
)

data class UpdatePortfolioStockRequest(
    @Schema(description = "목표 비중 (%)", example = "25.00")
    val targetWeight: BigDecimal? = null,

    @field:Size(max = 1000, message = "메모는 1000자를 초과할 수 없습니다")
    @Schema(description = "메모")
    val memo: String? = null
)

// ===== Response DTOs =====

data class UserPortfolioResponse(
    @Schema(description = "포트폴리오 ID")
    val id: Long,

    @Schema(description = "사용자 ID")
    val userId: Long,

    @Schema(description = "전략 ID")
    val strategyId: Long?,

    @Schema(description = "포트폴리오 이름")
    val name: String,

    @Schema(description = "설명")
    val description: String?,

    @Schema(description = "기본 포트폴리오 여부")
    val isDefault: Boolean,

    @Schema(description = "종목 수")
    val stockCount: Int,

    @Schema(description = "생성일")
    val createdAt: LocalDateTime,

    @Schema(description = "수정일")
    val updatedAt: LocalDateTime
)

data class PortfolioStockResponse(
    @Schema(description = "ID")
    val id: Long,

    @Schema(description = "포트폴리오 ID")
    val portfolioId: Long,

    @Schema(description = "종목 ID")
    val stockId: Long,

    @Schema(description = "종목 코드")
    val ticker: String?,

    @Schema(description = "종목명")
    val stockName: String?,

    @Schema(description = "목표 비중 (%)")
    val targetWeight: BigDecimal,

    @Schema(description = "전략 기본 종목 여부")
    val isFromStrategy: Boolean,

    @Schema(description = "메모")
    val memo: String?,

    @Schema(description = "추가일")
    val addedAt: LocalDateTime
)

data class PortfolioStockListResponse(
    @Schema(description = "종목 목록")
    val stocks: List<PortfolioStockResponse>,

    @Schema(description = "총 비중 합계 (%)")
    val totalWeight: BigDecimal,

    @Schema(description = "총 개수")
    val total: Int
)
