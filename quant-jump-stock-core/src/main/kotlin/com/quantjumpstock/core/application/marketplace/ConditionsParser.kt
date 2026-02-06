package com.quantjumpstock.core.application.marketplace

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 전략 conditions DSL JSON을 StrategyRuleDto 목록으로 변환하는 파서
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

    fun parseToRules(conditionsJson: String): List<StrategyRuleDto> {
        if (conditionsJson.isBlank() || conditionsJson == "{}") {
            return emptyList()
        }

        return try {
            val root = objectMapper.readTree(conditionsJson)
            val result = mutableListOf<StrategyRuleDto>()
            var idCounter = 1

            // rules 배열 파싱
            val rulesNode = root.get("rules")
            if (rulesNode != null && rulesNode.isArray) {
                for (ruleNode in rulesNode) {
                    val signalType = ruleNode.get("signal_type")?.asText() ?: ""
                    val type = mapSignalType(signalType)
                    val name = ruleNode.get("name")?.asText() ?: ""
                    val description = ruleNode.get("description")?.asText() ?: ""

                    // conditions 내 파라미터를 플래튼 (다중 조건 시 인덱스 접두사 사용)
                    val parameters = mutableMapOf<String, Any>()
                    val conditionsNode = ruleNode.get("conditions")
                    if (conditionsNode != null && conditionsNode.isArray) {
                        val condCount = conditionsNode.size()
                        for ((idx, condNode) in conditionsNode.withIndex()) {
                            val prefix = if (condCount > 1) "cond${idx + 1}_" else ""
                            condNode.get("indicator")?.asText()?.let { parameters["${prefix}indicator"] = it }
                            condNode.get("operator")?.asText()?.let { parameters["${prefix}operator"] = it }
                            condNode.get("value")?.asText()?.let { parameters["${prefix}compareValue"] = it }
                            val paramsNode = condNode.get("params")
                            if (paramsNode != null && paramsNode.isObject) {
                                paramsNode.fields().forEach { (key, value) ->
                                    parameters["$prefix$key"] = jsonNodeToValue(value)
                                }
                            }
                        }
                    }

                    // weight, logic도 포함
                    ruleNode.get("weight")?.asDouble()?.let { parameters["weight"] = it }
                    ruleNode.get("logic")?.asText()?.let { parameters["logic"] = it }

                    result.add(
                        StrategyRuleDto(
                            id = idCounter++,
                            name = name,
                            description = description,
                            type = type,
                            parameters = parameters
                        )
                    )
                }
            }

            // risk_management → filter 타입 규칙으로 추가
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

            result
        } catch (e: Exception) {
            logger.warn("conditions JSON 파싱 실패: ${e.message}")
            emptyList()
        }
    }

    private fun jsonNodeToValue(node: JsonNode): Any = when {
        node.isInt -> node.asInt()
        node.isLong -> node.asLong()
        node.isDouble || node.isFloat -> node.asDouble()
        node.isBoolean -> node.asBoolean()
        else -> node.asText()
    }

    private fun mapSignalType(signalType: String): String = when (signalType.lowercase()) {
        "buy" -> "entry"
        "sell" -> "exit"
        else -> "filter"
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
