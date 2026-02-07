package com.quantjumpstock.core.application.portfolio

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime

// ===== Request DTOs =====

data class AddDefaultStockRequest(
    @Schema(description = "종목 ID", required = true)
    val stockId: Long,

    @Schema(description = "목표 비중 (%, 0 초과 100 이하)", required = true, example = "25.00")
    val targetWeight: BigDecimal,

    @field:Size(max = 1000, message = "메모는 1000자를 초과할 수 없습니다")
    @Schema(description = "메모")
    val memo: String? = null
)

data class UpdateDefaultStockRequest(
    @Schema(description = "목표 비중 (%)", example = "30.00")
    val targetWeight: BigDecimal? = null,

    @field:Size(max = 1000, message = "메모는 1000자를 초과할 수 없습니다")
    @Schema(description = "메모")
    val memo: String? = null
)

data class ReplaceDefaultStocksRequest(
    @Schema(description = "기본 종목 목록", required = true)
    val stocks: List<DefaultStockItem>
)

data class DefaultStockItem(
    @Schema(description = "종목 ID", required = true)
    val stockId: Long,

    @Schema(description = "목표 비중 (%, 0 초과 100 이하)", required = true, example = "25.00")
    val targetWeight: BigDecimal,

    @field:Size(max = 1000, message = "메모는 1000자를 초과할 수 없습니다")
    @Schema(description = "메모")
    val memo: String? = null
)

// ===== Response DTOs =====

data class DefaultStockResponse(
    @Schema(description = "ID")
    val id: Long,

    @Schema(description = "전략 ID")
    val strategyId: Long,

    @Schema(description = "종목 ID")
    val stockId: Long,

    @Schema(description = "종목 코드")
    val ticker: String?,

    @Schema(description = "종목명")
    val stockName: String?,

    @Schema(description = "종목 영문명")
    val stockNameEn: String? = null,

    @Schema(description = "시장 (US, KR, CRYPTO)")
    val market: String? = null,

    @Schema(description = "목표 비중 (%)")
    val targetWeight: BigDecimal,

    @Schema(description = "메모")
    val memo: String?,

    @Schema(description = "생성일")
    val createdAt: LocalDateTime
)

data class DefaultStockListResponse(
    @Schema(description = "기본 종목 목록")
    val stocks: List<DefaultStockResponse>,

    @Schema(description = "총 비중 합계 (%)")
    val totalWeight: BigDecimal,

    @Schema(description = "총 개수")
    val total: Int
)

data class PortfolioResponse(
    @Schema(description = "성공 여부")
    val success: Boolean,

    @Schema(description = "메시지")
    val message: String? = null
)

open class PortfolioException(message: String) : RuntimeException(message)

class PortfolioNotFoundException(message: String) : PortfolioException(message)

class PortfolioAccessDeniedException(message: String) : PortfolioException(message)
