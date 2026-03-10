package com.quantjumpstock.core.application.dashboard

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Schema(description = "대시보드 응답")
data class DashboardResponse(
    @Schema(description = "사용자 정보")
    val user: DashboardUserDto,
    @Schema(description = "구독 정보")
    val subscriptions: DashboardSubscriptionsDto,
    @Schema(description = "알림/신호 정보")
    val signals: DashboardSignalsDto,
    @Schema(description = "시장 지표")
    val market: DashboardMarketDto,
    @Schema(description = "AI 사용량")
    val aiUsage: DashboardAiUsageDto
)

@Schema(description = "대시보드 사용자 정보")
data class DashboardUserDto(
    @Schema(description = "사용자명 (닉네임 또는 이름)")
    val nickname: String?,
    @Schema(description = "티어", example = "FREE")
    val tier: String,
    @Schema(description = "가입일", example = "2026-01-15")
    val joinDate: String
)

@Schema(description = "대시보드 구독 정보")
data class DashboardSubscriptionsDto(
    @Schema(description = "현재 구독 수")
    val count: Int,
    @Schema(description = "최대 구독 수 (-1은 무제한)")
    val maxCount: Long,
    @Schema(description = "구독 전략 요약 목록")
    val strategies: List<DashboardStrategyDto>
)

@Schema(description = "대시보드 전략 요약")
data class DashboardStrategyDto(
    @Schema(description = "전략 ID")
    val id: Long,
    @Schema(description = "전략명")
    val name: String,
    @Schema(description = "전략 설명")
    val description: String?
)

@Schema(description = "대시보드 알림/신호 정보")
data class DashboardSignalsDto(
    @Schema(description = "미읽음 알림 수")
    val unreadCount: Long,
    @Schema(description = "오늘 받은 알림 수")
    val todayCount: Long,
    @Schema(description = "최근 알림 목록 (최대 3건)")
    val recent: List<DashboardNotificationDto>
)

@Schema(description = "대시보드 알림 항목")
data class DashboardNotificationDto(
    @Schema(description = "알림 ID")
    val id: Long,
    @Schema(description = "알림 유형")
    val type: String,
    @Schema(description = "알림 제목")
    val title: String,
    @Schema(description = "생성 시각")
    val createdAt: String?
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        fun from(id: Long, type: String, title: String, createdAt: LocalDateTime?): DashboardNotificationDto =
            DashboardNotificationDto(
                id = id,
                type = type,
                title = title,
                createdAt = createdAt?.format(formatter)
            )
    }
}

@Schema(description = "대시보드 시장 지표")
data class DashboardMarketDto(
    @Schema(description = "주요 시장 지수 목록")
    val indices: List<DashboardIndexDto>
)

@Schema(description = "시장 지수 정보")
data class DashboardIndexDto(
    @Schema(description = "지수 심볼", example = "^GSPC")
    val symbol: String,
    @Schema(description = "지수명", example = "S&P 500")
    val name: String?,
    @Schema(description = "현재 가격")
    val price: BigDecimal,
    @Schema(description = "전일 대비 변화율 (%)")
    val changePercent: BigDecimal?
)

@Schema(description = "대시보드 AI 사용량")
data class DashboardAiUsageDto(
    @Schema(description = "오늘 사용한 백테스트 횟수")
    val backtestUsed: Int,
    @Schema(description = "일일 백테스트 한도 (-1은 무제한)")
    val backtestLimit: Int,
    @Schema(description = "백테스트 남은 횟수 (-1은 무제한)")
    val backtestRemaining: Int
)
