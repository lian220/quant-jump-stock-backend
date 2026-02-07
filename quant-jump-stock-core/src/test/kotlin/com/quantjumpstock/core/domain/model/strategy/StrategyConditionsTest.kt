package com.quantjumpstock.core.domain.model.strategy

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class StrategyConditionsTest : DescribeSpec({

    describe("RiskManagement") {
        it("기본값으로 생성") {
            val rm = RiskManagement.DEFAULT

            rm.stopLossPct shouldBe 0.05
            rm.takeProfitPct shouldBe 0.15
            rm.maxPositionPct shouldBe 0.10
            rm.maxDrawdownPct shouldBe 0.20
            rm.trailingStop shouldBe false
            rm.trailingStopPct shouldBe 0.05
        }

        it("커스텀 값으로 생성") {
            val rm = RiskManagement(
                stopLossPct = 0.03,
                takeProfitPct = 0.20,
                maxPositionPct = 0.15,
                maxDrawdownPct = 0.25,
                trailingStop = true,
                trailingStopPct = 0.10
            )

            rm.stopLossPct shouldBe 0.03
            rm.trailingStop shouldBe true
        }

        it("경계값 0.0, 1.0 허용") {
            RiskManagement(
                stopLossPct = 0.0,
                takeProfitPct = 1.0,
                maxPositionPct = 0.0,
                maxDrawdownPct = 1.0,
                trailingStopPct = 0.0
            )
        }

        it("stopLossPct 범위 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                RiskManagement(stopLossPct = -0.01)
            }
            shouldThrow<IllegalArgumentException> {
                RiskManagement(stopLossPct = 1.01)
            }
        }

        it("takeProfitPct 범위 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                RiskManagement(takeProfitPct = -0.01)
            }
        }

        it("maxPositionPct 범위 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                RiskManagement(maxPositionPct = 1.5)
            }
        }

        it("maxDrawdownPct 범위 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                RiskManagement(maxDrawdownPct = -1.0)
            }
        }

        it("trailingStopPct 범위 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                RiskManagement(trailingStopPct = 2.0)
            }
        }
    }

    describe("StrategyRule") {
        val sampleCondition = ConditionBlock.RsiFilter(
            operator = ConditionOperator.LT,
            threshold = BigDecimal("30")
        )

        it("유효한 파라미터로 생성") {
            val rule = StrategyRule(
                name = "RSI 과매도 매수",
                signalType = SignalType.BUY,
                conditions = listOf(sampleCondition)
            )

            rule.name shouldBe "RSI 과매도 매수"
            rule.signalType shouldBe SignalType.BUY
            rule.logic shouldBe ConditionLogic.AND
            rule.weight shouldBe 1.0
        }

        it("빈 name 시 예외") {
            shouldThrow<IllegalArgumentException> {
                StrategyRule(
                    name = "",
                    signalType = SignalType.BUY,
                    conditions = listOf(sampleCondition)
                )
            }
            shouldThrow<IllegalArgumentException> {
                StrategyRule(
                    name = "   ",
                    signalType = SignalType.BUY,
                    conditions = listOf(sampleCondition)
                )
            }
        }

        it("빈 conditions 시 예외") {
            shouldThrow<IllegalArgumentException> {
                StrategyRule(
                    name = "Empty Rule",
                    signalType = SignalType.BUY,
                    conditions = emptyList()
                )
            }
        }

        it("weight 범위 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                StrategyRule(
                    name = "Bad Weight",
                    signalType = SignalType.BUY,
                    conditions = listOf(sampleCondition),
                    weight = -0.1
                )
            }
            shouldThrow<IllegalArgumentException> {
                StrategyRule(
                    name = "Bad Weight",
                    signalType = SignalType.BUY,
                    conditions = listOf(sampleCondition),
                    weight = 1.1
                )
            }
        }

        it("weight 경계값 0.0, 1.0 허용") {
            StrategyRule(
                name = "Zero Weight",
                signalType = SignalType.BUY,
                conditions = listOf(sampleCondition),
                weight = 0.0
            )
            StrategyRule(
                name = "Full Weight",
                signalType = SignalType.BUY,
                conditions = listOf(sampleCondition),
                weight = 1.0
            )
        }

        it("여러 조건 결합") {
            val conditions = listOf(
                ConditionBlock.SmaCrossover(shortPeriod = 20, longPeriod = 50),
                ConditionBlock.RsiFilter(operator = ConditionOperator.LT, threshold = BigDecimal("70"))
            )

            val rule = StrategyRule(
                name = "Golden Cross + RSI",
                signalType = SignalType.BUY,
                conditions = conditions,
                logic = ConditionLogic.AND
            )

            rule.conditions.size shouldBe 2
        }
    }

    describe("StrategyDefinition") {
        val buyRule = StrategyRule(
            name = "골든크로스 매수",
            signalType = SignalType.BUY,
            conditions = listOf(
                ConditionBlock.SmaCrossover(shortPeriod = 20, longPeriod = 50)
            )
        )

        val sellRule = StrategyRule(
            name = "데드크로스 매도",
            signalType = SignalType.SELL,
            conditions = listOf(
                ConditionBlock.SmaCrossover(
                    shortPeriod = 20,
                    longPeriod = 50,
                    crossDirection = ConditionOperator.CROSSES_BELOW
                )
            )
        )

        it("유효한 파라미터로 생성") {
            val def = StrategyDefinition(rules = listOf(buyRule, sellRule))

            def.rules.size shouldBe 2
            def.riskManagement shouldBe RiskManagement.DEFAULT
        }

        it("빈 rules 시 예외") {
            shouldThrow<IllegalArgumentException> {
                StrategyDefinition(rules = emptyList())
            }
        }

        it("buyRules() 필터링") {
            val def = StrategyDefinition(rules = listOf(buyRule, sellRule))

            def.buyRules().size shouldBe 1
            def.buyRules().first().signalType shouldBe SignalType.BUY
        }

        it("sellRules() 필터링") {
            val def = StrategyDefinition(rules = listOf(buyRule, sellRule))

            def.sellRules().size shouldBe 1
            def.sellRules().first().signalType shouldBe SignalType.SELL
        }

        it("allConditionTypes() 수집") {
            val mixedDef = StrategyDefinition(
                rules = listOf(
                    buyRule,
                    StrategyRule(
                        name = "RSI 필터",
                        signalType = SignalType.BUY,
                        conditions = listOf(
                            ConditionBlock.RsiFilter(
                                operator = ConditionOperator.LT,
                                threshold = BigDecimal("30")
                            ),
                            ConditionBlock.PerFilter(
                                operator = ConditionOperator.LT,
                                threshold = BigDecimal("15")
                            )
                        )
                    )
                )
            )

            mixedDef.allConditionTypes() shouldContainExactlyInAnyOrder
                    setOf("SMA_CROSSOVER", "RSI_FILTER", "PER_FILTER")
        }

        it("커스텀 RiskManagement 설정") {
            val customRm = RiskManagement(
                stopLossPct = 0.03,
                takeProfitPct = 0.20,
                trailingStop = true,
                trailingStopPct = 0.08
            )

            val def = StrategyDefinition(
                rules = listOf(buyRule),
                riskManagement = customRm
            )

            def.riskManagement.stopLossPct shouldBe 0.03
            def.riskManagement.trailingStop shouldBe true
        }

        it("매수 규칙만 있는 전략") {
            val def = StrategyDefinition(rules = listOf(buyRule))

            def.buyRules().size shouldBe 1
            def.sellRules().size shouldBe 0
        }
    }
})
