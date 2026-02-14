package com.quantjumpstock.core.application.marketplace

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.quantjumpstock.core.domain.model.strategy.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * 전략 conditions DSL JSON을 도메인 모델 또는 StrategyRuleDto 목록으로 변환하는 파서
 *
 * DSL 형식:
 * {
 *   "rules": [
 *     { "name": "...", "signal_type": "buy|sell", "description": "...",
 *       "conditions": [{ "indicator": "sma", "params": {...}, "operator": "crosses_above", "value": "sma_50" }],
 *       "weight": 1.0, "logic": "and" }
 *   ],
 *   "risk_management": { "stop_loss_pct": 0.05, "take_profit_pct": 0.15, "max_position_pct": 0.1 }
 * }
 */
@Component
class ConditionsParser(
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * JSON → StrategyDefinition 도메인 모델로 변환
     *
     * @return 파싱된 StrategyDefinition, 빈 JSON이나 파싱 실패 시 null
     */
    fun parseToDefinition(conditionsJson: String): StrategyDefinition? {
        if (conditionsJson.isBlank() || conditionsJson.trim() == "{}") {
            return null
        }

        return try {
            val root = objectMapper.readTree(conditionsJson)

            val rules = mutableListOf<StrategyRule>()

            val rulesNode = root.get("rules")
            if (rulesNode != null && rulesNode.isArray) {
                for (ruleNode in rulesNode) {
                    val name = ruleNode.get("name")?.asText() ?: ""
                    if (name.isBlank()) continue

                    val signalTypeCode = ruleNode.get("signal_type")?.asText() ?: ""
                    val signalType = try {
                        SignalType.fromDslCode(signalTypeCode)
                    } catch (e: IllegalArgumentException) {
                        logger.warn("미지원 signal_type: $signalTypeCode, 규칙 '$name' 건너뜀")
                        continue
                    }

                    val logicCode = ruleNode.get("logic")?.asText() ?: "and"
                    val logic = try {
                        ConditionLogic.fromDslCode(logicCode)
                    } catch (e: IllegalArgumentException) {
                        ConditionLogic.AND
                    }

                    val weight = ruleNode.get("weight")?.asDouble() ?: 1.0
                    val description = ruleNode.get("description")?.asText()

                    val conditions = mutableListOf<ConditionBlock>()
                    val conditionsNode = ruleNode.get("conditions")
                    if (conditionsNode != null && conditionsNode.isArray) {
                        for (condNode in conditionsNode) {
                            val block = parseCondition(condNode)
                            if (block != null) {
                                conditions.add(block)
                            }
                        }
                    }

                    if (conditions.isEmpty()) continue

                    rules.add(
                        StrategyRule(
                            name = name,
                            signalType = signalType,
                            conditions = conditions,
                            logic = logic,
                            weight = weight.coerceIn(0.0, 1.0),
                            description = description
                        )
                    )
                }
            }

            if (rules.isEmpty()) return null

            val riskNode = root.get("risk_management")
            val riskManagement = if (riskNode != null && riskNode.isObject) {
                parseRiskManagement(riskNode)
            } else {
                RiskManagement.DEFAULT
            }

            StrategyDefinition(rules = rules, riskManagement = riskManagement)
        } catch (e: Exception) {
            logger.warn("conditions JSON 파싱 실패: ${e.message}")
            null
        }
    }

    /**
     * JSON → StrategyRuleDto 목록으로 변환 (기존 API 하위호환 유지)
     *
     * 내부적으로 parseToDefinition()을 호출하여 도메인 모델을 거친 후 DTO로 변환.
     */
    fun parseToRules(conditionsJson: String): List<StrategyRuleDto> {
        val definition = parseToDefinition(conditionsJson) ?: return emptyList()

        val result = mutableListOf<StrategyRuleDto>()
        var idCounter = 1

        for (rule in definition.rules) {
            val type = when (rule.signalType) {
                SignalType.BUY -> "entry"
                SignalType.SELL -> "exit"
                SignalType.HOLD -> "filter"
            }

            val parameters = mutableMapOf<String, Any>()
            val condCount = rule.conditions.size
            for ((idx, cond) in rule.conditions.withIndex()) {
                val prefix = if (condCount > 1) "cond${idx + 1}_" else ""
                flattenConditionBlock(cond, prefix, parameters)
            }

            parameters["weight"] = rule.weight
            parameters["logic"] = rule.logic.dslCode

            result.add(
                StrategyRuleDto(
                    id = idCounter++,
                    name = rule.name,
                    description = rule.description ?: "",
                    type = type,
                    parameters = parameters
                )
            )
        }

        // risk_management → filter 타입 규칙으로 추가
        val rm = definition.riskManagement
        if (rm != RiskManagement.DEFAULT) {
            val riskParams = mutableMapOf<String, Any>(
                "stop_loss_pct" to rm.stopLossPct,
                "take_profit_pct" to rm.takeProfitPct,
                "max_position_pct" to rm.maxPositionPct,
                "max_drawdown_pct" to rm.maxDrawdownPct,
                "trailing_stop" to rm.trailingStop,
                "trailing_stop_pct" to rm.trailingStopPct
            )
            result.add(
                StrategyRuleDto(
                    id = idCounter,
                    name = "리스크 관리",
                    description = buildRiskDescription(riskParams),
                    type = "filter",
                    parameters = riskParams
                )
            )
        } else {
            // risk_management가 JSON에 명시적으로 있었는지 원본에서 재확인
            try {
                val root = objectMapper.readTree(conditionsJson)
                val riskNode = root.get("risk_management")
                if (riskNode != null && riskNode.isObject) {
                    val riskParams = mutableMapOf<String, Any>()
                    riskNode.fields().forEach { (key, value) ->
                        riskParams[key] = jsonNodeToValue(value)
                    }
                    if (riskParams.isNotEmpty()) {
                        result.add(
                            StrategyRuleDto(
                                id = idCounter,
                                name = "리스크 관리",
                                description = buildRiskDescription(riskParams),
                                type = "filter",
                                parameters = riskParams
                            )
                        )
                    }
                }
            } catch (_: Exception) { /* 이미 parseToDefinition에서 처리됨 */ }
        }

        return result
    }

    // --- Private helpers ---

    private fun parseCondition(condNode: JsonNode): ConditionBlock? {
        val indicator = condNode.get("indicator")?.asText() ?: return null
        val operatorCode = condNode.get("operator")?.asText() ?: return null
        val value = condNode.get("value")?.asText() ?: ""
        val paramsNode = condNode.get("params")

        return try {
            when (indicator.lowercase()) {
                "sma", "ema" -> {
                    val operator = ConditionOperator.fromDslCode(operatorCode)
                    val shortPeriod = paramsNode?.get("period")?.asInt() ?: 20
                    val longPeriod = extractPeriodFromReference(value) ?: 50
                    val maType = IndicatorType.fromDslCode(indicator.lowercase())
                    ConditionBlock.SmaCrossover(
                        shortPeriod = shortPeriod,
                        longPeriod = longPeriod,
                        movingAverageType = maType,
                        crossDirection = operator
                    )
                }

                "rsi" -> {
                    val operator = ConditionOperator.fromDslCode(operatorCode)
                    val period = paramsNode?.get("period")?.asInt() ?: 14
                    val threshold = BigDecimal(value)
                    ConditionBlock.RsiFilter(
                        period = period,
                        operator = operator,
                        threshold = threshold
                    )
                }

                "macd" -> {
                    val operator = ConditionOperator.fromDslCode(operatorCode)
                    val shortPeriod = paramsNode?.get("short_period")?.asInt() ?: 12
                    val longPeriod = paramsNode?.get("long_period")?.asInt() ?: 26
                    val signalPeriod = paramsNode?.get("signal_period")?.asInt() ?: 9
                    ConditionBlock.MacdSignal(
                        shortPeriod = shortPeriod,
                        longPeriod = longPeriod,
                        signalPeriod = signalPeriod,
                        crossDirection = operator
                    )
                }

                "per" -> {
                    val operator = ConditionOperator.fromDslCode(operatorCode)
                    val threshold = BigDecimal(value)
                    ConditionBlock.PerFilter(operator = operator, threshold = threshold)
                }

                "pbr" -> {
                    val operator = ConditionOperator.fromDslCode(operatorCode)
                    val threshold = BigDecimal(value)
                    ConditionBlock.PbrFilter(operator = operator, threshold = threshold)
                }

                "dividend_yield" -> {
                    val operator = ConditionOperator.fromDslCode(operatorCode)
                    val threshold = BigDecimal(value)
                    ConditionBlock.DividendYield(operator = operator, threshold = threshold)
                }

                "roe", "earnings_growth", "debt_to_equity", "forward_pe" -> {
                    val operator = ConditionOperator.fromDslCode(operatorCode)
                    val threshold = BigDecimal(value)
                    val indicatorType = IndicatorType.fromDslCode(indicator.lowercase())
                    ConditionBlock.FundamentalFilter(
                        indicator = indicatorType,
                        operator = operator,
                        threshold = threshold
                    )
                }

                else -> {
                    logger.warn("미지원 indicator: $indicator, 조건 건너뜀")
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn("조건 파싱 실패 (indicator=$indicator): ${e.message}")
            null
        }
    }

    private fun parseRiskManagement(riskNode: JsonNode): RiskManagement {
        return try {
            RiskManagement(
                stopLossPct = riskNode.get("stop_loss_pct")?.asDouble() ?: 0.05,
                takeProfitPct = riskNode.get("take_profit_pct")?.asDouble() ?: 0.15,
                maxPositionPct = riskNode.get("max_position_pct")?.asDouble() ?: 0.10,
                maxDrawdownPct = riskNode.get("max_drawdown_pct")?.asDouble() ?: 0.20,
                trailingStop = riskNode.get("trailing_stop")?.asBoolean() ?: false,
                trailingStopPct = riskNode.get("trailing_stop_pct")?.asDouble() ?: 0.05
            )
        } catch (e: Exception) {
            logger.warn("risk_management 파싱 실패, 기본값 사용: ${e.message}")
            RiskManagement.DEFAULT
        }
    }

    /**
     * "sma_50" → 50, "ema_200" → 200 형태에서 숫자 추출
     */
    private fun extractPeriodFromReference(value: String): Int? {
        val match = Regex(""".*_(\d+)""").find(value)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * ConditionBlock → flat Map 변환 (기존 StrategyRuleDto.parameters 형식 유지)
     */
    private fun flattenConditionBlock(block: ConditionBlock, prefix: String, params: MutableMap<String, Any>) {
        when (block) {
            is ConditionBlock.SmaCrossover -> {
                params["${prefix}indicator"] = block.movingAverageType.dslCode
                params["${prefix}operator"] = block.crossDirection.dslCode
                params["${prefix}compareValue"] = "${block.movingAverageType.dslCode}_${block.longPeriod}"
                params["${prefix}period"] = block.shortPeriod
            }

            is ConditionBlock.RsiFilter -> {
                params["${prefix}indicator"] = "rsi"
                params["${prefix}operator"] = block.operator.dslCode
                params["${prefix}compareValue"] = block.threshold.toPlainString()
                params["${prefix}period"] = block.period
            }

            is ConditionBlock.MacdSignal -> {
                params["${prefix}indicator"] = "macd"
                params["${prefix}operator"] = block.crossDirection.dslCode
                params["${prefix}compareValue"] = "macd_signal"
                params["${prefix}short_period"] = block.shortPeriod
                params["${prefix}long_period"] = block.longPeriod
                params["${prefix}signal_period"] = block.signalPeriod
            }

            is ConditionBlock.PerFilter -> {
                params["${prefix}indicator"] = "per"
                params["${prefix}operator"] = block.operator.dslCode
                params["${prefix}compareValue"] = block.threshold.toPlainString()
            }

            is ConditionBlock.PbrFilter -> {
                params["${prefix}indicator"] = "pbr"
                params["${prefix}operator"] = block.operator.dslCode
                params["${prefix}compareValue"] = block.threshold.toPlainString()
            }

            is ConditionBlock.DividendYield -> {
                params["${prefix}indicator"] = "dividend_yield"
                params["${prefix}operator"] = block.operator.dslCode
                params["${prefix}compareValue"] = block.threshold.toPlainString()
            }

            is ConditionBlock.FundamentalFilter -> {
                params["${prefix}indicator"] = block.indicator.dslCode
                params["${prefix}operator"] = block.operator.dslCode
                params["${prefix}compareValue"] = block.threshold.toPlainString()
            }

            is ConditionBlock.Allocation -> {
                params["${prefix}indicator"] = "allocation"
                block.targetTicker?.let { params["${prefix}target_ticker"] = it }
                params["${prefix}target_weight_pct"] = block.targetWeightPct.toPlainString()
                params["${prefix}tolerance"] = block.tolerance.toPlainString()
            }
        }
    }

    private fun jsonNodeToValue(node: JsonNode): Any = when {
        node.isInt -> node.asInt()
        node.isLong -> node.asLong()
        node.isDouble || node.isFloat -> node.asDouble()
        node.isBoolean -> node.asBoolean()
        else -> node.asText()
    }

    private fun buildRiskDescription(params: Map<String, Any>): String {
        val parts = mutableListOf<String>()
        params["stop_loss_pct"]?.let { parts.add("손절 ${formatPct(it)}%") }
        params["take_profit_pct"]?.let { parts.add("익절 ${formatPct(it)}%") }
        params["max_position_pct"]?.let { parts.add("최대 포지션 ${formatPct(it)}%") }
        return if (parts.isNotEmpty()) parts.joinToString(", ") else "리스크 관리 설정"
    }

    private fun formatPct(value: Any): String {
        val num = when (value) {
            is Number -> value.toDouble() * 100
            else -> return value.toString()
        }
        return if (num == num.toLong().toDouble()) num.toLong().toString() else "%.1f".format(num)
    }
}
