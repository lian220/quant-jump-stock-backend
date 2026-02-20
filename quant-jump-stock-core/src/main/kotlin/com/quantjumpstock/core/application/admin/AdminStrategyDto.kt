package com.quantjumpstock.core.application.admin

import com.quantjumpstock.core.domain.model.strategy.RebalanceFrequency
import com.quantjumpstock.core.domain.model.strategy.StockSelectionType
import com.quantjumpstock.core.domain.model.strategy.StrategyStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 관리자용 전략 목록 응답
 */
data class AdminStrategyListResponse(
    @Schema(description = "전략 목록")
    val strategies: List<AdminStrategySummary>,

    @Schema(description = "총 개수")
    val total: Long,

    @Schema(description = "현재 페이지")
    val page: Int,

    @Schema(description = "페이지 크기")
    val size: Int,

    @Schema(description = "총 페이지 수")
    val totalPages: Int
)

/**
 * 관리자용 전략 요약
 */
data class AdminStrategySummary(
    @Schema(description = "전략 ID")
    val id: Long,

    @Schema(description = "전략 이름")
    val name: String,

    @Schema(description = "전략 설명")
    val description: String?,

    @Schema(description = "카테고리 코드")
    val categoryCode: String?,

    @Schema(description = "카테고리 이름")
    val categoryName: String?,

    @Schema(description = "소유자 ID")
    val ownerId: Long?,

    @Schema(description = "소유자 이름")
    val ownerName: String?,

    @Schema(description = "소유자 이메일")
    val ownerEmail: String?,

    @Schema(description = "상태")
    val status: StrategyStatus,

    @Schema(description = "종목선정 방식")
    val stockSelectionType: StockSelectionType,

    @Schema(description = "공개 여부")
    val isPublic: Boolean,

    @Schema(description = "프리미엄 여부")
    val isPremium: Boolean,

    @Schema(description = "리밸런싱 주기")
    val rebalanceFrequency: RebalanceFrequency,

    @Schema(description = "구독자 수")
    val subscriberCount: Int,

    @Schema(description = "평균 평점")
    val averageRating: BigDecimal,

    @Schema(description = "최신 CAGR (%)")
    val latestCagr: BigDecimal?,

    @Schema(description = "최신 MDD (%)")
    val latestMdd: BigDecimal?,

    @Schema(description = "생성일")
    val createdAt: LocalDateTime,

    @Schema(description = "수정일")
    val updatedAt: LocalDateTime
)

/**
 * 전략 상태 변경 요청
 */
data class ChangeStrategyStatusRequest(
    @Schema(description = "변경할 상태", example = "APPROVED", required = true)
    val status: StrategyStatus,

    @Schema(description = "사유 (반려 시 필수)", example = "전략 조건이 불명확합니다.")
    val reason: String? = null
)

/**
 * 전략 상태 변경 응답
 */
data class ChangeStrategyStatusResponse(
    @Schema(description = "성공 여부")
    val success: Boolean,

    @Schema(description = "전략 ID")
    val strategyId: Long,

    @Schema(description = "이전 상태")
    val previousStatus: StrategyStatus,

    @Schema(description = "변경된 상태")
    val newStatus: StrategyStatus,

    @Schema(description = "메시지")
    val message: String? = null
)

/**
 * 관리자용 통계 응답
 */
data class AdminStrategyStatsResponse(
    @Schema(description = "전체 전략 수")
    val total: Long,

    @Schema(description = "검토 대기 수")
    val pendingReview: Long,

    @Schema(description = "발행된 전략 수")
    val published: Long,

    @Schema(description = "총 구독자 수")
    val totalSubscribers: Long
)
