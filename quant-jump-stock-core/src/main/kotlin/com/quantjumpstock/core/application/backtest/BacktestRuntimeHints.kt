package com.quantjumpstock.core.application.backtest

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

/**
 * 백테스트 DTO의 GraalVM Native Image reflection 등록
 *
 * BacktestController의 ResponseEntity<Any> 반환 메서드에서 사용되는 DTO들은
 * Spring AOT가 타입을 추론할 수 없으므로, Jackson 직렬화를 위해
 * 명시적으로 reflection 힌트를 등록해야 합니다.
 */
class BacktestRuntimeHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val dtoClasses = listOf(
            // 백테스트 실행 응답 (POST /run → 202)
            BacktestRunResponse::class.java,

            // 백테스트 진행 상태 (GET /{id} → 202 RUNNING)
            BacktestPendingResponse::class.java,

            // 백테스트 결과 응답 (GET /{id} → 200)
            BacktestResultResponse::class.java,
            BacktestMetrics::class.java,
            EquityCurvePoint::class.java,
            BacktestTradeResponse::class.java,

            // Rate Limit 응답 (POST /run → 429)
            BacktestRateLimitResponse::class.java,

            // Enhanced 결과 응답 (GET /{id}/enhanced → 200)
            BacktestEnhancedResponse::class.java,
            MetricGradeResponse::class.java,
            GradedMetricResponse::class.java,
            GlossaryTerm::class.java,

            // 요청 DTO (Jackson 역직렬화)
            BacktestRunRequest::class.java,
            RiskSettings::class.java,
            StopLossSettings::class.java,
            TakeProfitSettings::class.java,
            TrailingStopSettings::class.java,
            PositionSizing::class.java,
            TradingCosts::class.java,
            SlippageModel::class.java,

            // Enum 타입
            StopType::class.java,
            PositionSizingMethod::class.java,
            SlippageType::class.java,
            RebalancePeriod::class.java,
            ExitReason::class.java,
            com.quantjumpstock.core.domain.model.backtest.UniverseType::class.java,
            com.quantjumpstock.core.domain.model.backtest.BacktestType::class.java,

            // 목록/페이징 응답
            BacktestListItemResponse::class.java,
            PagedResponse::class.java,
            BenchmarkResponse::class.java,
        )

        dtoClasses.forEach { dtoClass ->
            hints.reflection().registerType(
                dtoClass,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
            )
        }
    }
}
