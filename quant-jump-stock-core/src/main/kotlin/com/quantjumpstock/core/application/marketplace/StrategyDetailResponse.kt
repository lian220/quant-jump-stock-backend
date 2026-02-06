package com.quantjumpstock.core.application.marketplace

import com.quantjumpstock.core.application.strategy.CategoryInfo
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 전략 상세 응답
 */
@Schema(description = "전략 상세 정보")
data class StrategyDetailResponse(
    @Schema(description = "전략 ID")
    val id: Long,

    @Schema(description = "전략 이름")
    val name: String,

    @Schema(description = "전략 설명")
    val description: String?,

    @Schema(description = "카테고리 정보")
    val category: CategoryInfo,

    @Schema(description = "프리미엄 여부")
    val isPremium: Boolean,

    @Schema(description = "구독자 수")
    val subscriberCount: Int,

    @Schema(description = "평균 평점")
    val averageRating: BigDecimal,

    @Schema(description = "리밸런싱 주기")
    val rebalanceFrequency: String,

    @Schema(description = "성과 지표")
    val performanceMetrics: PerformanceMetricsDto?,

    @Schema(description = "수익 곡선")
    val equityCurve: List<EquityCurvePointDto>,

    @Schema(description = "현재 보유 종목")
    val currentHoldings: List<CurrentHoldingDto>,

    @Schema(description = "전략 규칙 목록")
    val rules: List<StrategyRuleDto> = emptyList(),

    @Schema(description = "월별 수익률")
    val monthlyReturns: List<MonthlyReturnDto> = emptyList(),

    @Schema(description = "거래 내역")
    val trades: List<BacktestTradeDto> = emptyList(),

    @Schema(description = "생성일")
    val createdAt: LocalDateTime
)

/**
 * 성과 지표
 */
@Schema(description = "전략 성과 지표")
data class PerformanceMetricsDto(
    @Schema(description = "CAGR (%)", example = "15.5")
    val cagr: BigDecimal,

    @Schema(description = "MDD (%)", example = "-12.3")
    val mdd: BigDecimal,

    @Schema(description = "샤프 비율", example = "1.25")
    val sharpeRatio: BigDecimal?,

    @Schema(description = "소르티노 비율", example = "1.45")
    val sortinoRatio: BigDecimal?,

    @Schema(description = "총 수익률 (%)", example = "125.5")
    val totalReturn: BigDecimal,

    @Schema(description = "변동성 (%)", example = "18.2")
    val volatility: BigDecimal?,

    @Schema(description = "승률 (%)", example = "65.5")
    val winRate: BigDecimal?,

    @Schema(description = "총 거래 수", example = "150")
    val totalTrades: Int,

    @Schema(description = "수익 거래 수", example = "98")
    val winningTrades: Int,

    @Schema(description = "손실 거래 수", example = "52")
    val losingTrades: Int,

    @Schema(description = "평균 수익률 (%)", example = "3.5")
    val avgWin: BigDecimal?,

    @Schema(description = "평균 손실률 (%)", example = "-2.1")
    val avgLoss: BigDecimal?,

    @Schema(description = "벤치마크 수익률 (%)", example = "45.2")
    val benchmarkReturn: BigDecimal?,

    @Schema(description = "알파", example = "0.08")
    val alpha: BigDecimal?,

    @Schema(description = "베타", example = "0.95")
    val beta: BigDecimal?,

    @Schema(description = "초기 자본", example = "10000000")
    val initialCapital: BigDecimal,

    @Schema(description = "최종 자산", example = "22550000")
    val finalValue: BigDecimal,

    @Schema(description = "백테스트 시작일")
    val startDate: String,

    @Schema(description = "백테스트 종료일")
    val endDate: String
)

/**
 * 수익 곡선 포인트
 */
@Schema(description = "수익 곡선 데이터 포인트")
data class EquityCurvePointDto(
    @Schema(description = "날짜", example = "2024-01-15")
    val date: String,

    @Schema(description = "자산 가치", example = "10500000")
    val value: BigDecimal,

    @Schema(description = "벤치마크 가치 (초기자본 기준 정규화)", example = "10200000")
    val benchmark: BigDecimal? = null
)

/**
 * 현재 보유 종목
 */
@Schema(description = "현재 보유 종목 정보")
data class CurrentHoldingDto(
    @Schema(description = "종목 코드", example = "005930")
    val ticker: String,

    @Schema(description = "목표 비중 (%)", example = "15.0")
    val targetWeight: BigDecimal?,

    @Schema(description = "신호 발생일", example = "2024-01-20")
    val signalDate: String,

    @Schema(description = "매수 사유")
    val reason: String?
)

/**
 * 전략 규칙
 */
@Schema(description = "전략 규칙 정보")
data class StrategyRuleDto(
    @Schema(description = "규칙 ID", example = "1")
    val id: Int,

    @Schema(description = "규칙 이름", example = "골든 크로스 매수")
    val name: String,

    @Schema(description = "규칙 설명", example = "단기 이평선이 장기 이평선 상향 돌파")
    val description: String,

    @Schema(description = "규칙 유형 (entry, exit, filter, rebalance)", example = "entry")
    val type: String,

    @Schema(description = "규칙 파라미터")
    val parameters: Map<String, Any>
)

/**
 * 월별 수익률
 */
@Schema(description = "월별 수익률 정보")
data class MonthlyReturnDto(
    @Schema(description = "연도", example = "2024")
    val year: Int,

    @Schema(description = "월 (1-12)", example = "3")
    val month: Int,

    @Schema(description = "수익률 (%)", example = "2.35")
    val returnPct: BigDecimal
)

/**
 * 거래 내역
 */
@Schema(description = "거래 내역 정보")
data class BacktestTradeDto(
    @Schema(description = "거래일", example = "2024-01-15")
    val tradeDate: String,

    @Schema(description = "종목 코드", example = "AAPL")
    val ticker: String,

    @Schema(description = "매매 방향 (BUY/SELL)", example = "BUY")
    val side: String,

    @Schema(description = "수량", example = "10")
    val quantity: Int,

    @Schema(description = "가격", example = "185.50")
    val price: BigDecimal,

    @Schema(description = "거래 금액", example = "1855.00")
    val amount: BigDecimal,

    @Schema(description = "손익 (SELL만)", example = "150.00")
    val pnl: BigDecimal?,

    @Schema(description = "손익률 (SELL만, %)", example = "8.50")
    val pnlPercent: BigDecimal?,

    @Schema(description = "보유일수 (SELL만)", example = "45")
    val holdingDays: Int?,

    @Schema(description = "매매 사유")
    val signalReason: String?
)
