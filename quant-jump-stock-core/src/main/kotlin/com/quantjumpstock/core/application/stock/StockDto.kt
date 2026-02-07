package com.quantjumpstock.core.application.stock

import com.quantjumpstock.core.domain.model.stock.DesignationStatus
import com.quantjumpstock.core.domain.model.stock.Market
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

// ===== Request DTOs =====

data class CreateStockRequest(
    @Schema(description = "종목 코드", example = "AAPL", required = true)
    val ticker: String,

    @Schema(description = "종목명", example = "Apple Inc.", required = true)
    val stockName: String,

    @Schema(description = "영문 종목명", example = "Apple Inc.")
    val stockNameEn: String? = null,

    @Schema(description = "거래소", example = "NASDAQ")
    val exchange: String? = null,

    @Schema(description = "섹터", example = "Technology")
    val sector: String? = null,

    @Schema(description = "산업", example = "Consumer Electronics")
    val industry: String? = null,

    @Schema(description = "시장 구분", example = "US")
    val market: Market = Market.KR,

    @Schema(description = "ETF 여부", example = "false")
    val isEtf: Boolean = false,

    @Schema(description = "레버리지 티커", example = "TQQQ")
    val leverageTicker: String? = null
)

data class UpdateStockRequest(
    @Schema(description = "종목명")
    val stockName: String? = null,

    @Schema(description = "영문 종목명")
    val stockNameEn: String? = null,

    @Schema(description = "거래소")
    val exchange: String? = null,

    @Schema(description = "섹터")
    val sector: String? = null,

    @Schema(description = "산업")
    val industry: String? = null,

    @Schema(description = "시장 구분")
    val market: Market? = null,

    @Schema(description = "ETF 여부")
    val isEtf: Boolean? = null,

    @Schema(description = "레버리지 티커")
    val leverageTicker: String? = null,

    @Schema(description = "활성 여부")
    val isActive: Boolean? = null
)

data class ChangeDesignationRequest(
    @Schema(description = "지정 상태", example = "WARNING", required = true)
    val designationStatus: DesignationStatus,

    @Schema(description = "지정 사유", example = "투자주의 환기종목 지정")
    val reason: String? = null
)

// ===== Response DTOs =====

data class StockResponse(
    @Schema(description = "성공 여부")
    val success: Boolean,

    @Schema(description = "종목 ID")
    val stockId: Long? = null,

    @Schema(description = "메시지")
    val message: String? = null
)

data class StockDetailResponse(
    @Schema(description = "종목 ID")
    val id: Long,

    @Schema(description = "종목 코드")
    val ticker: String,

    @Schema(description = "종목명")
    val stockName: String,

    @Schema(description = "영문 종목명")
    val stockNameEn: String?,

    @Schema(description = "거래소")
    val exchange: String?,

    @Schema(description = "섹터")
    val sector: String?,

    @Schema(description = "산업")
    val industry: String?,

    @Schema(description = "시장 구분")
    val market: Market,

    @Schema(description = "ETF 여부")
    val isEtf: Boolean,

    @Schema(description = "레버리지 티커")
    val leverageTicker: String?,

    @Schema(description = "지정 상태")
    val designationStatus: DesignationStatus,

    @Schema(description = "지정 사유")
    val designationReason: String?,

    @Schema(description = "지정일시")
    val designatedAt: LocalDateTime?,

    @Schema(description = "활성 여부")
    val isActive: Boolean,

    @Schema(description = "생성일")
    val createdAt: LocalDateTime,

    @Schema(description = "수정일")
    val updatedAt: LocalDateTime
)

data class StockSummary(
    @Schema(description = "종목 ID")
    val id: Long,

    @Schema(description = "종목 코드")
    val ticker: String,

    @Schema(description = "종목명")
    val stockName: String,

    @Schema(description = "영문 종목명")
    val stockNameEn: String?,

    @Schema(description = "시장 구분")
    val market: Market,

    @Schema(description = "섹터")
    val sector: String?,

    @Schema(description = "ETF 여부")
    val isEtf: Boolean,

    @Schema(description = "지정 상태")
    val designationStatus: DesignationStatus,

    @Schema(description = "활성 여부")
    val isActive: Boolean
)

data class StockSearchResponse(
    @Schema(description = "종목 목록")
    val stocks: List<StockSummary>,

    @Schema(description = "총 개수")
    val totalElements: Long,

    @Schema(description = "총 페이지 수")
    val totalPages: Int,

    @Schema(description = "현재 페이지")
    val currentPage: Int
)

data class DesignationHistoryResponse(
    @Schema(description = "이력 ID")
    val id: Long,

    @Schema(description = "이전 상태")
    val previousStatus: DesignationStatus,

    @Schema(description = "새 상태")
    val newStatus: DesignationStatus,

    @Schema(description = "사유")
    val reason: String?,

    @Schema(description = "변경자 ID")
    val changedBy: Long?,

    @Schema(description = "변경일시")
    val changedAt: LocalDateTime
)

class StockException(message: String) : RuntimeException(message)
