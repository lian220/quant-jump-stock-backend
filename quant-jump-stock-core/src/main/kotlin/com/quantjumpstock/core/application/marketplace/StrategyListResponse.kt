package com.quantjumpstock.core.application.marketplace

import com.quantjumpstock.core.application.strategy.CategoryInfo
import com.quantjumpstock.core.domain.model.strategy.StockSelectionType
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 공개 전략 목록 응답
 */
data class StrategyListResponse(
    @Schema(description = "전략 목록")
    val strategies: List<StrategyDto>,

    @Schema(description = "페이지 정보")
    val pagination: PaginationDto
)

data class StrategyDto(
    @Schema(description = "전략 ID")
    val id: Long,

    @Schema(description = "전략 이름")
    val name: String,

    @Schema(description = "전략 설명")
    val description: String?,

    @Schema(description = "카테고리")
    val category: CategoryInfo,

    @Schema(description = "프리미엄 여부")
    val isPremium: Boolean,

    @Schema(description = "종목선정 방식")
    val stockSelectionType: StockSelectionType,

    @Schema(description = "구독자 수")
    val subscriberCount: Int,

    @Schema(description = "평균 평점")
    val averageRating: BigDecimal,

    @Schema(description = "리밸런싱 주기")
    val rebalanceFrequency: String,

    @Schema(description = "백테스트 결과")
    val backtestResult: BacktestResultDto?,

    @Schema(description = "생성일")
    val createdAt: LocalDateTime
)

data class BacktestResultDto(
    @Schema(description = "CAGR (%)")
    val cagr: BigDecimal,

    @Schema(description = "MDD (%)")
    val mdd: BigDecimal,

    @Schema(description = "샤프 비율")
    val sharpeRatio: BigDecimal?,

    @Schema(description = "총 수익률 (%)")
    val totalReturn: BigDecimal,

    @Schema(description = "변동성 (%)")
    val volatility: BigDecimal?,

    @Schema(description = "승률 (%)")
    val winRate: BigDecimal?,

    @Schema(description = "백테스트 시작일")
    val startDate: String,

    @Schema(description = "백테스트 종료일")
    val endDate: String
)

data class PaginationDto(
    @Schema(description = "현재 페이지")
    val currentPage: Int,

    @Schema(description = "페이지 크기")
    val pageSize: Int,

    @Schema(description = "총 요소 수")
    val totalElements: Long,

    @Schema(description = "총 페이지 수")
    val totalPages: Int,

    @Schema(description = "첫 페이지 여부")
    val isFirst: Boolean,

    @Schema(description = "마지막 페이지 여부")
    val isLast: Boolean
)
