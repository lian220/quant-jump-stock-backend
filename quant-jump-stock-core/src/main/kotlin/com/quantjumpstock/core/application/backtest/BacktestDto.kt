package com.quantjumpstock.core.application.backtest

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 백테스트 실행 요청 DTO
 * POST /api/v1/backtest/run
 */
data class BacktestRunRequest(
    val strategyId: Long,
    val startDate: String,              // yyyy-MM-dd
    val endDate: String,                // yyyy-MM-dd
    val initialCapital: BigDecimal,
    val benchmark: String = "KOSPI",
    val rebalancePeriod: RebalancePeriod = RebalancePeriod.MONTHLY,
    val tickers: List<String> = emptyList(),

    // SCRUM-258: 리스크 파라미터
    val riskSettings: RiskSettings? = null,
    val positionSizing: PositionSizing? = null,
    val tradingCosts: TradingCosts? = null
)

// ============================================================================
// SCRUM-258: 리스크 파라미터 DTOs
// ============================================================================

/**
 * 리스크 설정
 */
data class RiskSettings(
    val stopLoss: StopLossSettings? = null,
    val takeProfit: TakeProfitSettings? = null,
    val trailingStop: TrailingStopSettings? = null
)

/**
 * 손절 설정
 */
data class StopLossSettings(
    val enabled: Boolean = false,
    val type: StopType = StopType.PERCENTAGE,
    val value: BigDecimal? = null           // 예: -5.0 (%)
)

/**
 * 익절 설정
 */
data class TakeProfitSettings(
    val enabled: Boolean = false,
    val type: StopType = StopType.PERCENTAGE,
    val value: BigDecimal? = null           // 예: 10.0 (%)
)

/**
 * 트레일링 스탑 설정
 */
data class TrailingStopSettings(
    val enabled: Boolean = false,
    val type: StopType = StopType.PERCENTAGE,
    val value: BigDecimal? = null,          // 예: 3.0 (%)
    val activationThreshold: BigDecimal? = null  // 활성화 임계값
)

/**
 * 스탑 유형
 */
enum class StopType {
    PERCENTAGE,     // 퍼센트 기반
    FIXED_AMOUNT,   // 고정 금액
    ATR             // ATR 기반
}

/**
 * 포지션 사이징 설정
 */
data class PositionSizing(
    val method: PositionSizingMethod = PositionSizingMethod.FIXED_PERCENTAGE,
    val maxPositionPct: BigDecimal = BigDecimal("20.0"),  // 최대 포지션 비율 (%)
    val maxPositions: Int = 10,                            // 최대 포지션 수
    val riskPerTrade: BigDecimal? = null                   // 거래당 리스크 (%)
)

/**
 * 포지션 사이징 방법
 */
enum class PositionSizingMethod {
    FIXED_PERCENTAGE,   // 고정 비율
    EQUAL_WEIGHT,       // 동일 비중
    RISK_PARITY,        // 리스크 패리티
    KELLY,              // 켈리 공식
    VOLATILITY_TARGET   // 변동성 타겟
}

/**
 * 거래 비용 설정
 */
data class TradingCosts(
    val commission: BigDecimal = BigDecimal("0.00015"),   // 수수료 (0.015%)
    val tax: BigDecimal = BigDecimal("0.0023"),           // 세금 (0.23%)
    val slippageModel: SlippageModel? = null
)

/**
 * 슬리피지 모델
 */
data class SlippageModel(
    val type: SlippageType = SlippageType.FIXED,
    val baseSlippage: BigDecimal = BigDecimal("0.001"),   // 기본 슬리피지 (0.1%)
    val volumeImpact: BigDecimal? = null                   // 거래량 영향
)

/**
 * 슬리피지 유형
 */
enum class SlippageType {
    NONE,       // 슬리피지 없음
    FIXED,      // 고정 슬리피지
    ADAPTIVE,   // 적응형 (거래량 기반)
    RANDOM      // 랜덤 범위
}

/**
 * 리밸런싱 주기
 */
enum class RebalancePeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY
}

/**
 * 백테스트 실행 응답 DTO (202 Accepted)
 */
data class BacktestRunResponse(
    val backtestId: String,
    val status: String,
    val estimatedTime: Int,             // 예상 소요 시간 (초)
    val message: String
)

/**
 * 백테스트 결과 조회 응답 DTO
 * GET /api/v1/backtest/{id}
 */
data class BacktestResultResponse(
    val id: Long,
    val strategyId: Long,
    val strategyName: String?,
    val status: String,
    val metrics: BacktestMetrics?,
    val equityCurve: List<EquityCurvePoint>?,
    val benchmarkCurve: List<EquityCurvePoint>?,
    val trades: List<BacktestTradeResponse>?,
    val createdAt: LocalDateTime,
    val completedAt: LocalDateTime?
)

/**
 * 백테스트 성과 지표
 * SCRUM-259: 거래 비용 및 리스크 지표 확장
 */
data class BacktestMetrics(
    // 기본 성과 지표
    val cagr: BigDecimal,
    val mdd: BigDecimal,
    val sharpeRatio: BigDecimal?,
    val sortinoRatio: BigDecimal?,
    val winRate: BigDecimal?,
    val totalReturn: BigDecimal,
    val volatility: BigDecimal?,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val avgWin: BigDecimal?,
    val avgLoss: BigDecimal?,
    val benchmarkReturn: BigDecimal?,
    val alpha: BigDecimal?,
    val beta: BigDecimal?,

    // SCRUM-259: 거래 비용
    val totalCommission: BigDecimal? = null,
    val totalSlippage: BigDecimal? = null,
    val totalTax: BigDecimal? = null,
    val netProfitAfterCosts: BigDecimal? = null,

    // SCRUM-259: 리스크 지표
    val profitFactor: BigDecimal? = null,       // 총 이익 / 총 손실
    val expectancy: BigDecimal? = null,          // 기대값 (거래당 평균 수익)
    val kellyPercentage: BigDecimal? = null,     // 켈리 비율

    // SCRUM-259: 거래 분석
    val totalTradesStoppedOut: Int? = null,      // 손절 종료 거래 수
    val totalTradesTakenProfit: Int? = null,     // 익절 종료 거래 수
    val avgHoldingPeriod: BigDecimal? = null,    // 평균 보유 기간 (일)
    val bestTrade: BigDecimal? = null,           // 최고 수익 거래
    val worstTrade: BigDecimal? = null,          // 최대 손실 거래
    val maxConsecutiveWins: Int? = null,         // 최대 연속 승리
    val maxConsecutiveLosses: Int? = null        // 최대 연속 패배
)

/**
 * 자산 곡선 데이터 포인트
 */
data class EquityCurvePoint(
    val date: String,
    val value: BigDecimal
)

/**
 * 백테스트 거래 내역 응답 DTO
 * SCRUM-259: 거래 비용 상세 정보 확장
 */
data class BacktestTradeResponse(
    val id: Long,
    val tradeDate: LocalDate,
    val ticker: String,
    val side: String,
    val quantity: Int,
    val price: BigDecimal,
    val amount: BigDecimal,
    val commission: BigDecimal,
    val pnl: BigDecimal?,
    val pnlPercent: BigDecimal?,
    val holdingDays: Int?,
    val signalReason: String?,

    // SCRUM-259: 거래 비용 상세
    val executionPrice: BigDecimal? = null,      // 실제 체결가
    val slippageAmount: BigDecimal? = null,      // 슬리피지 금액
    val commissionAmount: BigDecimal? = null,    // 수수료 금액 (= commission)
    val taxAmount: BigDecimal? = null,           // 세금 금액
    val exitReason: ExitReason? = null           // 청산 사유
)

/**
 * 거래 청산 사유
 */
enum class ExitReason {
    SIGNAL,         // 전략 신호
    STOP_LOSS,      // 손절
    TAKE_PROFIT,    // 익절
    TRAILING_STOP,  // 트레일링 스탑
    REBALANCE,      // 리밸런싱
    FORCED,         // 강제 청산
    EXPIRED         // 만기
}

/**
 * 백테스트 목록 조회 응답 (요약 정보)
 * GET /api/v1/backtest
 */
data class BacktestListItemResponse(
    val id: Long,
    val strategyId: Long,
    val strategyName: String?,
    val status: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val initialCapital: BigDecimal,
    val finalValue: BigDecimal?,
    val totalReturn: BigDecimal?,
    val cagr: BigDecimal?,
    val mdd: BigDecimal?,
    val sharpeRatio: BigDecimal?,
    val createdAt: LocalDateTime,
    val completedAt: LocalDateTime?
)

/**
 * 페이징 응답 래퍼
 */
data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

/**
 * 백테스트 진행 상태 응답 (202 - 아직 처리 중)
 */
data class BacktestPendingResponse(
    val id: Long,
    val status: String,
    val message: String,
    val estimatedRemainingTime: Int?
)

/**
 * 백테스트 Rate Limit 체크 결과
 */
data class BacktestLimitResult(
    val allowed: Boolean,
    val remaining: Int,
    val dailyLimit: Int,
    val tier: String,
    val message: String? = null
)

/**
 * 백테스트 Rate Limit 초과 응답 (429 Too Many Requests)
 */
data class BacktestRateLimitResponse(
    val error: String = "RATE_LIMIT_EXCEEDED",
    val dailyLimit: Int,
    val remaining: Int,
    val tier: String,
    val message: String
)
