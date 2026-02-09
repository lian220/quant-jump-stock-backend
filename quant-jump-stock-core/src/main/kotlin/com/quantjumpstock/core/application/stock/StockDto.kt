package com.quantjumpstock.core.application.stock

import com.quantjumpstock.core.domain.model.stock.DesignationStatus
import com.quantjumpstock.core.domain.model.stock.Market
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

// ===== Request DTOs =====

data class CreateStockRequest(
    @field:NotBlank(message = "종목 코드는 필수입니다")
    @field:Size(max = 20, message = "종목 코드는 20자를 초과할 수 없습니다")
    @Schema(description = "종목 코드", example = "AAPL", required = true)
    val ticker: String,

    @field:NotBlank(message = "종목명은 필수입니다")
    @field:Size(max = 200, message = "종목명은 200자를 초과할 수 없습니다")
    @Schema(description = "종목명", example = "Apple Inc.", required = true)
    val stockName: String,

    @field:Size(max = 200, message = "영문 종목명은 200자를 초과할 수 없습니다")
    @Schema(description = "영문 종목명", example = "Apple Inc.")
    val stockNameEn: String? = null,

    @field:Size(max = 50, message = "거래소는 50자를 초과할 수 없습니다")
    @Schema(description = "거래소", example = "NASDAQ")
    val exchange: String? = null,

    @field:Size(max = 100, message = "섹터는 100자를 초과할 수 없습니다")
    @Schema(description = "섹터", example = "Technology")
    val sector: String? = null,

    @field:Size(max = 100, message = "산업은 100자를 초과할 수 없습니다")
    @Schema(description = "산업", example = "Consumer Electronics")
    val industry: String? = null,

    @Schema(description = "시장 구분", example = "US")
    val market: Market = Market.KR,

    @Schema(description = "ETF 여부", example = "false")
    val isEtf: Boolean = false,

    @field:Size(max = 20, message = "레버리지 티커는 20자를 초과할 수 없습니다")
    @Schema(description = "레버리지 티커", example = "TQQQ")
    val leverageTicker: String? = null
)

data class UpdateStockRequest(
    @field:Size(max = 200, message = "종목명은 200자를 초과할 수 없습니다")
    @Schema(description = "종목명")
    val stockName: String? = null,

    @field:Size(max = 200, message = "영문 종목명은 200자를 초과할 수 없습니다")
    @Schema(description = "영문 종목명")
    val stockNameEn: String? = null,

    @field:Size(max = 50, message = "거래소는 50자를 초과할 수 없습니다")
    @Schema(description = "거래소")
    val exchange: String? = null,

    @field:Size(max = 100, message = "섹터는 100자를 초과할 수 없습니다")
    @Schema(description = "섹터")
    val sector: String? = null,

    @field:Size(max = 100, message = "산업은 100자를 초과할 수 없습니다")
    @Schema(description = "산업")
    val industry: String? = null,

    @Schema(description = "시장 구분")
    val market: Market? = null,

    @Schema(description = "ETF 여부")
    val isEtf: Boolean? = null,

    @field:Size(max = 20, message = "레버리지 티커는 20자를 초과할 수 없습니다")
    @Schema(description = "레버리지 티커")
    val leverageTicker: String? = null,

    @Schema(description = "활성 여부")
    val isActive: Boolean? = null
)

data class ChangeDesignationRequest(
    @Schema(description = "지정 상태", example = "WARNING", required = true)
    val designationStatus: DesignationStatus,

    @field:Size(max = 500, message = "지정 사유는 500자를 초과할 수 없습니다")
    @Schema(description = "지정 사유", example = "투자주의 환기종목 지정")
    val reason: String? = null
)

data class StockSearchRequest(
    @Schema(description = "검색어 (종목 코드 또는 종목명)", example = "AAPL")
    val query: String? = null,

    @Schema(description = "시장 구분", example = "US")
    val market: Market? = null,

    @Schema(description = "활성 여부", example = "true")
    val isActive: Boolean? = null,

    @Schema(description = "페이지 번호 (0부터 시작)", example = "0")
    val page: Int = 0,

    @Schema(description = "페이지 크기", example = "20")
    val size: Int = 20
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
