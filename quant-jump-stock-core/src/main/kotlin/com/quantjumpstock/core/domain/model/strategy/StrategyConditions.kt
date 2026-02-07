package com.quantjumpstock.core.domain.model.strategy

/**
 * 리스크 관리 설정 - Value Object
 *
 * Python DSL RiskManagement와 1:1 매핑.
 * 모든 비율은 0.0 ~ 1.0 범위 (예: 0.05 = 5%)
 */
data class RiskManagement(
    val stopLossPct: Double = 0.05,
    val takeProfitPct: Double = 0.15,
    val maxPositionPct: Double = 0.10,
    val maxDrawdownPct: Double = 0.20,
    val trailingStop: Boolean = false,
    val trailingStopPct: Double = 0.05
) {
    init {
        require(stopLossPct in 0.0..1.0) { "stopLossPct must be between 0.0 and 1.0, got: $stopLossPct" }
        require(takeProfitPct in 0.0..1.0) { "takeProfitPct must be between 0.0 and 1.0, got: $takeProfitPct" }
        require(maxPositionPct in 0.0..1.0) { "maxPositionPct must be between 0.0 and 1.0, got: $maxPositionPct" }
        require(maxDrawdownPct in 0.0..1.0) { "maxDrawdownPct must be between 0.0 and 1.0, got: $maxDrawdownPct" }
        require(trailingStopPct in 0.0..1.0) { "trailingStopPct must be between 0.0 and 1.0, got: $trailingStopPct" }
    }

    companion object {
        val DEFAULT = RiskManagement()
    }
}

/**
 * 전략 규칙 - Python DSL Rule 매핑
 *
 * 여러 ConditionBlock을 AND/OR 논리로 결합한 매수/매도 규칙.
 */
data class StrategyRule(
    val name: String,
    val signalType: SignalType,
    val conditions: List<ConditionBlock>,
    val logic: ConditionLogic = ConditionLogic.AND,
    val weight: Double = 1.0,
    val description: String? = null
) {
    init {
        require(name.isNotBlank()) { "Rule name must not be blank" }
        require(conditions.isNotEmpty()) { "Rule must have at least one condition" }
        require(weight in 0.0..1.0) { "weight must be between 0.0 and 1.0, got: $weight" }
    }
}

/**
 * 전략 정의 - Python DSL StrategyDefinition 매핑
 *
 * Strategy.conditions JSON을 파싱한 도메인 모델.
 * 규칙 목록과 리스크 관리 설정을 포함.
 */
data class StrategyDefinition(
    val rules: List<StrategyRule>,
    val riskManagement: RiskManagement = RiskManagement.DEFAULT
) {
    init {
        require(rules.isNotEmpty()) { "StrategyDefinition must have at least one rule" }
    }

    fun buyRules(): List<StrategyRule> = rules.filter { it.signalType == SignalType.BUY }

    fun sellRules(): List<StrategyRule> = rules.filter { it.signalType == SignalType.SELL }

    fun allConditionTypes(): Set<String> = rules
        .flatMap { it.conditions }
        .map { it.type }
        .toSet()
}
