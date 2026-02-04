package com.quantjumpstock.core.application.strategy

import com.quantjumpstock.core.adapter.output.persistence.jpa.RebalanceFrequency
import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 카테고리 정보 (응답용)
 */
data class CategoryInfo(
    @Schema(description = "카테고리 ID")
    val id: Long,

    @Schema(description = "카테고리 코드", example = "MOMENTUM")
    val code: String,

    @Schema(description = "카테고리 이름", example = "모멘텀")
    val name: String
)

/**
 * 전략 생성 요청
 */
data class CreateStrategyRequest(
    @Schema(description = "전략 이름", example = "모멘텀 전략", required = true)
    val name: String,

    @Schema(description = "전략 설명", example = "최근 3개월 수익률 상위 종목 투자")
    val description: String? = null,

    @Schema(description = "카테고리 코드", example = "MOMENTUM", required = true)
    val categoryCode: String,

    @Schema(description = "공개 여부", example = "false")
    val isPublic: Boolean = false,

    @Schema(description = "프리미엄 여부", example = "false")
    val isPremium: Boolean = false,

    @Schema(description = "전략 조건 (JSON)", example = "{\"indicators\": [\"RSI\", \"MACD\"]}")
    val conditions: String = "{}",

    @Schema(description = "리밸런싱 주기", example = "MONTHLY")
    val rebalanceFrequency: RebalanceFrequency = RebalanceFrequency.MONTHLY
)

/**
 * 전략 수정 요청
 */
data class UpdateStrategyRequest(
    @Schema(description = "전략 이름", example = "모멘텀 전략 v2")
    val name: String? = null,

    @Schema(description = "전략 설명")
    val description: String? = null,

    @Schema(description = "카테고리 코드", example = "VALUE")
    val categoryCode: String? = null,

    @Schema(description = "공개 여부")
    val isPublic: Boolean? = null,

    @Schema(description = "프리미엄 여부")
    val isPremium: Boolean? = null,

    @Schema(description = "전략 상태")
    val status: StrategyStatus? = null,

    @Schema(description = "전략 조건 (JSON)")
    val conditions: String? = null,

    @Schema(description = "리밸런싱 주기")
    val rebalanceFrequency: RebalanceFrequency? = null
)

/**
 * 전략 상세 응답
 */
data class StrategyDetailResponse(
    @Schema(description = "전략 ID")
    val id: Long,

    @Schema(description = "전략 이름")
    val name: String,

    @Schema(description = "전략 설명")
    val description: String?,

    @Schema(description = "카테고리")
    val category: CategoryInfo,

    @Schema(description = "소유자 ID")
    val ownerId: Long?,

    @Schema(description = "소유자 이름")
    val ownerName: String?,

    @Schema(description = "공개 여부")
    val isPublic: Boolean,

    @Schema(description = "프리미엄 여부")
    val isPremium: Boolean,

    @Schema(description = "상태")
    val status: StrategyStatus,

    @Schema(description = "전략 조건 (JSON)")
    val conditions: String,

    @Schema(description = "리밸런싱 주기")
    val rebalanceFrequency: RebalanceFrequency,

    @Schema(description = "구독자 수")
    val subscriberCount: Int,

    @Schema(description = "평균 평점")
    val averageRating: BigDecimal,

    @Schema(description = "백테스트 결과")
    val backtestResults: List<BacktestResultSummary>,

    @Schema(description = "생성일")
    val createdAt: LocalDateTime,

    @Schema(description = "수정일")
    val updatedAt: LocalDateTime
)

/**
 * 백테스트 결과 요약
 */
data class BacktestResultSummary(
    @Schema(description = "백테스트 ID")
    val id: Long,

    @Schema(description = "CAGR (%)")
    val cagr: BigDecimal,

    @Schema(description = "MDD (%)")
    val mdd: BigDecimal,

    @Schema(description = "샤프 비율")
    val sharpeRatio: BigDecimal?,

    @Schema(description = "총 수익률 (%)")
    val totalReturn: BigDecimal,

    @Schema(description = "상태")
    val status: String,

    @Schema(description = "시작일")
    val startDate: String,

    @Schema(description = "종료일")
    val endDate: String
)

/**
 * 내 전략 목록 응답
 */
data class MyStrategiesResponse(
    @Schema(description = "전략 목록")
    val strategies: List<StrategySummary>,

    @Schema(description = "총 개수")
    val total: Int
)

/**
 * 전략 요약 (목록용)
 */
data class StrategySummary(
    @Schema(description = "전략 ID")
    val id: Long,

    @Schema(description = "전략 이름")
    val name: String,

    @Schema(description = "카테고리")
    val category: CategoryInfo,

    @Schema(description = "상태")
    val status: StrategyStatus,

    @Schema(description = "공개 여부")
    val isPublic: Boolean,

    @Schema(description = "프리미엄 여부")
    val isPremium: Boolean,

    @Schema(description = "구독자 수")
    val subscriberCount: Int,

    @Schema(description = "평균 평점")
    val averageRating: BigDecimal,

    @Schema(description = "최신 CAGR")
    val latestCagr: BigDecimal?,

    @Schema(description = "최신 MDD")
    val latestMdd: BigDecimal?,

    @Schema(description = "생성일")
    val createdAt: LocalDateTime
)

/**
 * 전략 생성/수정 응답
 */
data class StrategyResponse(
    @Schema(description = "성공 여부")
    val success: Boolean,

    @Schema(description = "전략 ID")
    val strategyId: Long? = null,

    @Schema(description = "메시지")
    val message: String? = null
)

/**
 * 전략 서비스 예외
 */
class StrategyException(message: String) : RuntimeException(message)
