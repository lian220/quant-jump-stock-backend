package com.quantjumpstock.core.domain.model

import com.quantjumpstock.core.domain.port.output.Benchmark
import java.math.BigDecimal

/** 백테스트 요청 Domain Model */
data class BacktestRequest(
        val requestId: String,
        val strategyId: Long,
        val startDate: String, // yyyy-MM-dd
        val endDate: String, // yyyy-MM-dd
        val initialCapital: BigDecimal,
        val timestamp: String,
        val source: String = "quantiq-core",
        val userId: String? = null,
        val tickers: List<String> = emptyList(), // 백테스트 대상 종목
        val benchmark: String = Benchmark.DEFAULT_TICKER,
        // SCRUM-337: 다중 벤치마크 지원
        val benchmarks: List<String> = listOf(Benchmark.DEFAULT_TICKER),
        val rebalancePeriod: String = "MONTHLY",

        // SCRUM-258: 리스크 파라미터
        val riskSettings: RiskSettingsModel? = null,
        val positionSizing: PositionSizingModel? = null,
        val tradingCosts: TradingCostsModel? = null,

        // SCRUM-344: 유니버스 타입 + 백테스트 분류
        val universeType: String = "MARKET",
        val backtestType: String = "USER_CUSTOM",

        // Admin 일괄 실행 시 체크포인트 무시하고 전체 재실행
        val forceFull: Boolean = false
)

/** 리스크 설정 Domain Model */
data class RiskSettingsModel(
        val stopLoss: StopSettingsModel? = null,
        val takeProfit: StopSettingsModel? = null,
        val trailingStop: TrailingStopModel? = null
)

data class StopSettingsModel(
        val enabled: Boolean = false,
        val type: String = "percentage", // percentage, fixed_amount, atr
        val value: BigDecimal? = null
)

data class TrailingStopModel(
        val enabled: Boolean = false,
        val type: String = "percentage",
        val value: BigDecimal? = null,
        val activationThreshold: BigDecimal? = null
)

/** 포지션 사이징 Domain Model */
data class PositionSizingModel(
        val method: String =
                "fixed_percentage", // fixed_percentage, equal_weight, risk_parity, kelly
        val maxPositionPct: BigDecimal = BigDecimal("20.0"),
        val maxPositions: Int = 10,
        val riskPerTrade: BigDecimal? = null
)

/** 거래 비용 Domain Model */
data class TradingCostsModel(
        val commission: BigDecimal = BigDecimal("0.00015"),
        val tax: BigDecimal = BigDecimal("0.0023"),
        val slippageModel: SlippageModelConfig? = null
)

data class SlippageModelConfig(
        val type: String = "fixed", // none, fixed, adaptive, random
        val baseSlippage: BigDecimal = BigDecimal("0.001"),
        val volumeImpact: BigDecimal? = null
)
