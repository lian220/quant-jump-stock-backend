package com.quantjumpstock.core.application.marketplace

import com.fasterxml.jackson.databind.ObjectMapper
import com.quantjumpstock.core.domain.model.strategy.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class ConditionsParserTest : DescribeSpec({

    val parser = ConditionsParser(ObjectMapper())

    describe("parseToDefinition") {

        it("Golden Cross JSON → SmaCrossover") {
            val json = """
            {
              "rules": [{
                "name": "골든 크로스 매수",
                "signal_type": "buy",
                "description": "20일 SMA가 50일 SMA 상향 돌파",
                "conditions": [{
                  "indicator": "sma",
                  "params": { "period": 20 },
                  "operator": "crosses_above",
                  "value": "sma_50"
                }],
                "weight": 1.0,
                "logic": "and"
              }]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()
            definition.rules shouldHaveSize 1

            val rule = definition.rules[0]
            rule.name shouldBe "골든 크로스 매수"
            rule.signalType shouldBe SignalType.BUY
            rule.logic shouldBe ConditionLogic.AND

            val cond = rule.conditions[0]
            cond.shouldBeInstanceOf<ConditionBlock.SmaCrossover>()
            cond.shortPeriod shouldBe 20
            cond.longPeriod shouldBe 50
            cond.movingAverageType shouldBe IndicatorType.SMA
            cond.crossDirection shouldBe ConditionOperator.CROSSES_ABOVE
        }

        it("EMA 크로스 → SmaCrossover with EMA type") {
            val json = """
            {
              "rules": [{
                "name": "EMA 크로스",
                "signal_type": "buy",
                "conditions": [{
                  "indicator": "ema",
                  "params": { "period": 12 },
                  "operator": "crosses_above",
                  "value": "ema_26"
                }],
                "weight": 1.0,
                "logic": "and"
              }]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()

            val cond = definition.rules[0].conditions[0]
            cond.shouldBeInstanceOf<ConditionBlock.SmaCrossover>()
            cond.movingAverageType shouldBe IndicatorType.EMA
            cond.shortPeriod shouldBe 12
            cond.longPeriod shouldBe 26
        }

        it("RSI 조건 → RsiFilter") {
            val json = """
            {
              "rules": [{
                "name": "RSI 과매도",
                "signal_type": "buy",
                "conditions": [{
                  "indicator": "rsi",
                  "params": { "period": 14 },
                  "operator": "lt",
                  "value": "30"
                }],
                "weight": 1.0,
                "logic": "and"
              }]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()

            val cond = definition.rules[0].conditions[0]
            cond.shouldBeInstanceOf<ConditionBlock.RsiFilter>()
            cond.period shouldBe 14
            cond.operator shouldBe ConditionOperator.LT
            cond.threshold shouldBe BigDecimal("30")
        }

        it("MACD 조건 → MacdSignal 기본값") {
            val json = """
            {
              "rules": [{
                "name": "MACD 시그널",
                "signal_type": "buy",
                "conditions": [{
                  "indicator": "macd",
                  "params": {},
                  "operator": "crosses_above",
                  "value": "macd_signal"
                }],
                "weight": 1.0,
                "logic": "and"
              }]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()

            val cond = definition.rules[0].conditions[0]
            cond.shouldBeInstanceOf<ConditionBlock.MacdSignal>()
            cond.shortPeriod shouldBe 12
            cond.longPeriod shouldBe 26
            cond.signalPeriod shouldBe 9
            cond.crossDirection shouldBe ConditionOperator.CROSSES_ABOVE
        }

        it("MACD 커스텀 파라미터") {
            val json = """
            {
              "rules": [{
                "name": "MACD 커스텀",
                "signal_type": "sell",
                "conditions": [{
                  "indicator": "macd",
                  "params": { "short_period": 8, "long_period": 21, "signal_period": 5 },
                  "operator": "crosses_below",
                  "value": "macd_signal"
                }],
                "weight": 0.8,
                "logic": "or"
              }]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()

            val rule = definition.rules[0]
            rule.signalType shouldBe SignalType.SELL
            rule.logic shouldBe ConditionLogic.OR
            rule.weight shouldBe 0.8

            val cond = rule.conditions[0]
            cond.shouldBeInstanceOf<ConditionBlock.MacdSignal>()
            cond.shortPeriod shouldBe 8
            cond.longPeriod shouldBe 21
            cond.signalPeriod shouldBe 5
            cond.crossDirection shouldBe ConditionOperator.CROSSES_BELOW
        }

        it("PER 조건 → PerFilter") {
            val json = """
            {
              "rules": [{
                "name": "저PER",
                "signal_type": "buy",
                "conditions": [{
                  "indicator": "per",
                  "operator": "lt",
                  "value": "15"
                }],
                "weight": 1.0,
                "logic": "and"
              }]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()

            val cond = definition.rules[0].conditions[0]
            cond.shouldBeInstanceOf<ConditionBlock.PerFilter>()
            cond.operator shouldBe ConditionOperator.LT
            cond.threshold shouldBe BigDecimal("15")
        }

        it("PBR 조건 → PbrFilter") {
            val json = """
            {
              "rules": [{
                "name": "저PBR",
                "signal_type": "buy",
                "conditions": [{
                  "indicator": "pbr",
                  "operator": "lte",
                  "value": "1.5"
                }],
                "weight": 1.0,
                "logic": "and"
              }]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()

            val cond = definition.rules[0].conditions[0]
            cond.shouldBeInstanceOf<ConditionBlock.PbrFilter>()
            cond.operator shouldBe ConditionOperator.LTE
            cond.threshold shouldBe BigDecimal("1.5")
        }

        it("배당수익률 조건 → DividendYield") {
            val json = """
            {
              "rules": [{
                "name": "고배당",
                "signal_type": "buy",
                "conditions": [{
                  "indicator": "dividend_yield",
                  "operator": "gte",
                  "value": "3"
                }],
                "weight": 1.0,
                "logic": "and"
              }]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()

            val cond = definition.rules[0].conditions[0]
            cond.shouldBeInstanceOf<ConditionBlock.DividendYield>()
            cond.operator shouldBe ConditionOperator.GTE
            cond.threshold shouldBe BigDecimal("3")
        }

        it("복합 규칙 (buyRule + sellRule)") {
            val json = """
            {
              "rules": [
                {
                  "name": "매수 규칙",
                  "signal_type": "buy",
                  "conditions": [
                    { "indicator": "sma", "params": { "period": 20 }, "operator": "crosses_above", "value": "sma_50" },
                    { "indicator": "rsi", "params": { "period": 14 }, "operator": "lt", "value": "30" }
                  ],
                  "weight": 1.0,
                  "logic": "and"
                },
                {
                  "name": "매도 규칙",
                  "signal_type": "sell",
                  "conditions": [
                    { "indicator": "rsi", "params": { "period": 14 }, "operator": "gt", "value": "70" }
                  ],
                  "weight": 1.0,
                  "logic": "and"
                }
              ]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()
            definition.rules shouldHaveSize 2
            definition.buyRules() shouldHaveSize 1
            definition.sellRules() shouldHaveSize 1

            val buyRule = definition.buyRules()[0]
            buyRule.conditions shouldHaveSize 2
            buyRule.conditions[0].shouldBeInstanceOf<ConditionBlock.SmaCrossover>()
            buyRule.conditions[1].shouldBeInstanceOf<ConditionBlock.RsiFilter>()
        }

        it("AND/OR logic 매핑") {
            val json = """
            {
              "rules": [
                {
                  "name": "AND 규칙",
                  "signal_type": "buy",
                  "conditions": [{ "indicator": "rsi", "operator": "lt", "value": "30" }],
                  "logic": "and"
                },
                {
                  "name": "OR 규칙",
                  "signal_type": "buy",
                  "conditions": [{ "indicator": "rsi", "operator": "lt", "value": "25" }],
                  "logic": "or"
                }
              ]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()
            definition.rules[0].logic shouldBe ConditionLogic.AND
            definition.rules[1].logic shouldBe ConditionLogic.OR
        }

        it("risk_management → RiskManagement 필드값") {
            val json = """
            {
              "rules": [{
                "name": "매수",
                "signal_type": "buy",
                "conditions": [{ "indicator": "rsi", "operator": "lt", "value": "30" }]
              }],
              "risk_management": {
                "stop_loss_pct": 0.07,
                "take_profit_pct": 0.20,
                "max_position_pct": 0.15,
                "max_drawdown_pct": 0.25,
                "trailing_stop": true,
                "trailing_stop_pct": 0.03
              }
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()

            val rm = definition.riskManagement
            rm.stopLossPct shouldBe 0.07
            rm.takeProfitPct shouldBe 0.20
            rm.maxPositionPct shouldBe 0.15
            rm.maxDrawdownPct shouldBe 0.25
            rm.trailingStop shouldBe true
            rm.trailingStopPct shouldBe 0.03
        }

        it("risk_management 없으면 기본값") {
            val json = """
            {
              "rules": [{
                "name": "매수",
                "signal_type": "buy",
                "conditions": [{ "indicator": "rsi", "operator": "lt", "value": "30" }]
              }]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()
            definition.riskManagement shouldBe RiskManagement.DEFAULT
        }

        it("빈 JSON → null") {
            parser.parseToDefinition("").shouldBeNull()
            parser.parseToDefinition("  ").shouldBeNull()
            parser.parseToDefinition("{}").shouldBeNull()
        }

        it("잘못된 JSON → null (예외 아님)") {
            parser.parseToDefinition("not valid json").shouldBeNull()
            parser.parseToDefinition("{invalid").shouldBeNull()
        }

        it("미지원 indicator는 건너뜀") {
            val json = """
            {
              "rules": [{
                "name": "미지원 조건",
                "signal_type": "buy",
                "conditions": [
                  { "indicator": "unknown_indicator", "operator": "gt", "value": "100" }
                ]
              }]
            }
            """.trimIndent()

            parser.parseToDefinition(json).shouldBeNull()
        }

        it("ROE 조건 → FundamentalFilter") {
            val json = """
            {
              "rules": [{
                "name": "고ROE",
                "signal_type": "buy",
                "conditions": [{
                  "indicator": "roe",
                  "operator": "gt",
                  "value": "15"
                }],
                "weight": 1.0,
                "logic": "and"
              }]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()

            val cond = definition.rules[0].conditions[0]
            cond.shouldBeInstanceOf<ConditionBlock.FundamentalFilter>()
            cond.indicator shouldBe IndicatorType.ROE
            cond.operator shouldBe ConditionOperator.GT
            cond.threshold shouldBe BigDecimal("15")
        }

        it("earnings_growth 조건 → FundamentalFilter") {
            val json = """
            {
              "rules": [{
                "name": "이익성장",
                "signal_type": "buy",
                "conditions": [{
                  "indicator": "earnings_growth",
                  "operator": "gte",
                  "value": "10"
                }],
                "weight": 1.0,
                "logic": "and"
              }]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()

            val cond = definition.rules[0].conditions[0]
            cond.shouldBeInstanceOf<ConditionBlock.FundamentalFilter>()
            cond.indicator shouldBe IndicatorType.EARNINGS_GROWTH
            cond.operator shouldBe ConditionOperator.GTE
            cond.threshold shouldBe BigDecimal("10")
        }

        it("debt_to_equity 조건 → FundamentalFilter") {
            val json = """
            {
              "rules": [{
                "name": "저부채",
                "signal_type": "buy",
                "conditions": [{
                  "indicator": "debt_to_equity",
                  "operator": "lt",
                  "value": "100"
                }],
                "weight": 1.0,
                "logic": "and"
              }]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()

            val cond = definition.rules[0].conditions[0]
            cond.shouldBeInstanceOf<ConditionBlock.FundamentalFilter>()
            cond.indicator shouldBe IndicatorType.DEBT_TO_EQUITY
        }

        it("forward_pe 조건 → FundamentalFilter") {
            val json = """
            {
              "rules": [{
                "name": "저선행PER",
                "signal_type": "buy",
                "conditions": [{
                  "indicator": "forward_pe",
                  "operator": "lte",
                  "value": "20"
                }],
                "weight": 1.0,
                "logic": "and"
              }]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()

            val cond = definition.rules[0].conditions[0]
            cond.shouldBeInstanceOf<ConditionBlock.FundamentalFilter>()
            cond.indicator shouldBe IndicatorType.FORWARD_PE
        }

        it("빈 이름의 규칙은 건너뜀") {
            val json = """
            {
              "rules": [{
                "name": "",
                "signal_type": "buy",
                "conditions": [{ "indicator": "rsi", "operator": "lt", "value": "30" }]
              }]
            }
            """.trimIndent()

            parser.parseToDefinition(json).shouldBeNull()
        }

        it("weight 기본값 1.0, logic 기본값 and") {
            val json = """
            {
              "rules": [{
                "name": "기본값 테스트",
                "signal_type": "buy",
                "conditions": [{ "indicator": "rsi", "operator": "lt", "value": "30" }]
              }]
            }
            """.trimIndent()

            val definition = parser.parseToDefinition(json)
            definition.shouldNotBeNull()
            definition.rules[0].weight shouldBe 1.0
            definition.rules[0].logic shouldBe ConditionLogic.AND
        }
    }

    describe("parseToRules - 하위호환") {

        it("Golden Cross → StrategyRuleDto(type=entry)") {
            val json = """
            {
              "rules": [{
                "name": "골든 크로스 매수",
                "signal_type": "buy",
                "description": "20일 SMA가 50일 SMA 상향 돌파",
                "conditions": [{
                  "indicator": "sma",
                  "params": { "period": 20 },
                  "operator": "crosses_above",
                  "value": "sma_50"
                }],
                "weight": 1.0,
                "logic": "and"
              }]
            }
            """.trimIndent()

            val rules = parser.parseToRules(json)
            rules shouldHaveSize 1

            val rule = rules[0]
            rule.id shouldBe 1
            rule.name shouldBe "골든 크로스 매수"
            rule.type shouldBe "entry"
            rule.description shouldBe "20일 SMA가 50일 SMA 상향 돌파"

            val params = rule.parameters
            params["indicator"] shouldBe "sma"
            params["operator"] shouldBe "crosses_above"
            params["compareValue"] shouldBe "sma_50"
            params["period"] shouldBe 20
        }

        it("signal_type 매핑 (buy→entry, sell→exit)") {
            val json = """
            {
              "rules": [
                {
                  "name": "매수",
                  "signal_type": "buy",
                  "conditions": [{ "indicator": "rsi", "operator": "lt", "value": "30" }]
                },
                {
                  "name": "매도",
                  "signal_type": "sell",
                  "conditions": [{ "indicator": "rsi", "operator": "gt", "value": "70" }]
                }
              ]
            }
            """.trimIndent()

            val rules = parser.parseToRules(json)
            rules shouldHaveSize 2
            rules[0].type shouldBe "entry"
            rules[1].type shouldBe "exit"
        }

        it("risk_management → StrategyRuleDto(type=filter, name=리스크 관리)") {
            val json = """
            {
              "rules": [{
                "name": "매수",
                "signal_type": "buy",
                "conditions": [{ "indicator": "rsi", "operator": "lt", "value": "30" }]
              }],
              "risk_management": {
                "stop_loss_pct": 0.05,
                "take_profit_pct": 0.15,
                "max_position_pct": 0.1
              }
            }
            """.trimIndent()

            val rules = parser.parseToRules(json)
            val riskRule = rules.find { it.type == "filter" && it.name == "리스크 관리" }
            riskRule.shouldNotBeNull()
            riskRule.parameters["stop_loss_pct"] shouldBe 0.05
            riskRule.parameters["take_profit_pct"] shouldBe 0.15
            riskRule.parameters["max_position_pct"] shouldBe 0.1
        }

        it("다중 조건 시 인덱스 접두사") {
            val json = """
            {
              "rules": [{
                "name": "복합 매수",
                "signal_type": "buy",
                "conditions": [
                  { "indicator": "sma", "params": { "period": 20 }, "operator": "crosses_above", "value": "sma_50" },
                  { "indicator": "rsi", "params": { "period": 14 }, "operator": "lt", "value": "30" }
                ],
                "weight": 1.0,
                "logic": "and"
              }]
            }
            """.trimIndent()

            val rules = parser.parseToRules(json)
            rules shouldHaveSize 1

            val params = rules[0].parameters
            params["cond1_indicator"] shouldBe "sma"
            params["cond1_operator"] shouldBe "crosses_above"
            params["cond1_compareValue"] shouldBe "sma_50"
            params["cond1_period"] shouldBe 20
            params["cond2_indicator"] shouldBe "rsi"
            params["cond2_operator"] shouldBe "lt"
            params["cond2_compareValue"] shouldBe "30"
            params["cond2_period"] shouldBe 14
        }

        it("빈 JSON → emptyList()") {
            parser.parseToRules("") shouldHaveSize 0
            parser.parseToRules("  ") shouldHaveSize 0
            parser.parseToRules("{}") shouldHaveSize 0
        }

        it("잘못된 JSON → emptyList()") {
            parser.parseToRules("not valid") shouldHaveSize 0
        }

        it("weight, logic 파라미터 포함") {
            val json = """
            {
              "rules": [{
                "name": "테스트",
                "signal_type": "buy",
                "conditions": [{ "indicator": "rsi", "operator": "lt", "value": "30" }],
                "weight": 0.7,
                "logic": "or"
              }]
            }
            """.trimIndent()

            val rules = parser.parseToRules(json)
            rules[0].parameters["weight"] shouldBe 0.7
            rules[0].parameters["logic"] shouldBe "or"
        }

        it("MACD 파라미터 플래튼") {
            val json = """
            {
              "rules": [{
                "name": "MACD",
                "signal_type": "buy",
                "conditions": [{
                  "indicator": "macd",
                  "params": { "short_period": 8, "long_period": 21, "signal_period": 5 },
                  "operator": "crosses_above",
                  "value": "macd_signal"
                }]
              }]
            }
            """.trimIndent()

            val rules = parser.parseToRules(json)
            val params = rules[0].parameters
            params["indicator"] shouldBe "macd"
            params["operator"] shouldBe "crosses_above"
            params["compareValue"] shouldBe "macd_signal"
            params["short_period"] shouldBe 8
            params["long_period"] shouldBe 21
            params["signal_period"] shouldBe 5
        }

        it("순차 ID 부여") {
            val json = """
            {
              "rules": [
                { "name": "규칙1", "signal_type": "buy", "conditions": [{ "indicator": "rsi", "operator": "lt", "value": "30" }] },
                { "name": "규칙2", "signal_type": "sell", "conditions": [{ "indicator": "rsi", "operator": "gt", "value": "70" }] }
              ],
              "risk_management": { "stop_loss_pct": 0.05 }
            }
            """.trimIndent()

            val rules = parser.parseToRules(json)
            rules[0].id shouldBe 1
            rules[1].id shouldBe 2
            rules[2].id shouldBe 3  // 리스크 관리
        }
    }
})
