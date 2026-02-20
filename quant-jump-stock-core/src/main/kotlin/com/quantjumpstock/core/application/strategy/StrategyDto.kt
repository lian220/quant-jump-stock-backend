package com.quantjumpstock.core.application.strategy

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.quantjumpstock.core.domain.model.backtest.UniverseType
import com.quantjumpstock.core.domain.model.strategy.RebalanceFrequency
import com.quantjumpstock.core.domain.model.strategy.StockSelectionType
import com.quantjumpstock.core.domain.model.strategy.StrategyStatus
import com.quantjumpstock.core.infrastructure.json.JsonStringDeserializer
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate
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

    @Schema(description = "종목선정 방식", example = "SCREENING")
    val stockSelectionType: StockSelectionType = StockSelectionType.SCREENING,

    @Schema(description = "투자 철학 (AI 매매 판단 시 참고)", example = "장기 가치투자, 경제적 해자 중시")
    val investmentPhilosophy: String? = null,

    @Schema(description = "전략 조건 (JSON)", example = "{\"indicators\": [\"RSI\", \"MACD\"]}")
    @JsonDeserialize(using = JsonStringDeserializer::class)
    val conditions: String = "{}",

    @Schema(description = "리밸런싱 주기", example = "MONTHLY")
    val rebalanceFrequency: RebalanceFrequency = RebalanceFrequency.MONTHLY,

    @Schema(description = "기본 리스크 설정 (JSON)", example = "{\"stopLoss\":{\"enabled\":true,\"percentage\":5}}")
    @JsonDeserialize(using = JsonStringDeserializer::class)
    val riskSettings: String = "{}",

    @Schema(description = "기본 포지션 사이징 설정 (JSON)", example = "{\"method\":\"fixed_percentage\",\"maxPositionPct\":20}")
    @JsonDeserialize(using = JsonStringDeserializer::class)
    val positionSizing: String = "{}",

    @Schema(description = "기본 거래 비용 설정 (JSON)", example = "{\"commissionRate\":0.015,\"taxRate\":0.23}")
    @JsonDeserialize(using = JsonStringDeserializer::class)
    val tradingCosts: String = "{}"
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

    @Schema(description = "종목선정 방식")
    val stockSelectionType: StockSelectionType? = null,

    @Schema(description = "투자 철학 (AI 매매 판단 시 참고)")
    val investmentPhilosophy: String? = null,

    @Schema(description = "전략 조건 (JSON)")
    @JsonDeserialize(using = JsonStringDeserializer::class)
    val conditions: String? = null,

    @Schema(description = "리밸런싱 주기")
    val rebalanceFrequency: RebalanceFrequency? = null,

    @Schema(description = "기본 리스크 설정 (JSON)")
    @JsonDeserialize(using = JsonStringDeserializer::class)
    val riskSettings: String? = null,

    @Schema(description = "기본 포지션 사이징 설정 (JSON)")
    @JsonDeserialize(using = JsonStringDeserializer::class)
    val positionSizing: String? = null,

    @Schema(description = "기본 거래 비용 설정 (JSON)")
    @JsonDeserialize(using = JsonStringDeserializer::class)
    val tradingCosts: String? = null
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

    @Schema(description = "종목선정 방식")
    val stockSelectionType: StockSelectionType,

    @Schema(description = "투자 철학")
    val investmentPhilosophy: String?,

    @Schema(description = "전략 조건 (JSON)")
    val conditions: String,

    @Schema(description = "리밸런싱 주기")
    val rebalanceFrequency: RebalanceFrequency,

    @Schema(description = "기본 리스크 설정 (JSON)")
    val riskSettings: String = "{}",

    @Schema(description = "기본 포지션 사이징 설정 (JSON)")
    val positionSizing: String = "{}",

    @Schema(description = "기본 거래 비용 설정 (JSON)")
    val tradingCosts: String = "{}",

    @Schema(description = "구독자 수")
    val subscriberCount: Int,

    @Schema(description = "평균 평점")
    val averageRating: BigDecimal,

    // SCRUM-344: 유니버스 설정
    @Schema(description = "추천 유니버스 타입")
    val recommendedUniverseType: UniverseType = UniverseType.MARKET,

    @Schema(description = "지원 유니버스 타입 목록")
    val supportedUniverseTypes: List<UniverseType> = listOf(UniverseType.MARKET, UniverseType.PORTFOLIO, UniverseType.FIXED),

    // SCRUM-344: 대표 백테스트
    @Schema(description = "대표(Canonical) 백테스트 결과")
    val canonicalBacktest: CanonicalBacktestSummary? = null,

    @Schema(description = "백테스트 결과")
    val backtestResults: List<BacktestResultSummary>,

    @Schema(description = "생성일")
    val createdAt: LocalDateTime,

    @Schema(description = "수정일")
    val updatedAt: LocalDateTime,

    @Schema(description = "현재 사용자의 구독 여부 (비로그인 시 false)")
    val isSubscribed: Boolean = false,

    @Schema(description = "현재 사용자의 구독 ID (구독 중이 아니면 null)")
    val subscriptionId: Long? = null
)

/**
 * SCRUM-344: Canonical 백테스트 요약
 */
data class CanonicalBacktestSummary(
    @Schema(description = "백테스트 ID")
    val id: Long,

    @Schema(description = "CAGR (%)")
    val cagr: BigDecimal,

    @Schema(description = "MDD (%)")
    val mdd: BigDecimal,

    @Schema(description = "샤프 비율")
    val sharpeRatio: BigDecimal?,

    @Schema(description = "소르티노 비율")
    val sortinoRatio: BigDecimal?,

    @Schema(description = "총 수익률 (%)")
    val totalReturn: BigDecimal,

    @Schema(description = "변동성")
    val volatility: BigDecimal?,

    @Schema(description = "승률 (%)")
    val winRate: BigDecimal?,

    @Schema(description = "벤치마크 수익률 (%)")
    val benchmarkReturn: BigDecimal?,

    @Schema(description = "알파")
    val alpha: BigDecimal?,

    @Schema(description = "시작일")
    val startDate: LocalDate,

    @Schema(description = "종료일")
    val endDate: LocalDate,

    @Schema(description = "에쿼티 커브 (JSON)")
    val equityCurve: String?
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

    @Schema(description = "종목선정 방식")
    val stockSelectionType: StockSelectionType,

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
