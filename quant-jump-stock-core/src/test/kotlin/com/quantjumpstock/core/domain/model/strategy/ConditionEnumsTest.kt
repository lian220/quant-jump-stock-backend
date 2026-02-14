package com.quantjumpstock.core.domain.model.strategy

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ConditionEnumsTest : DescribeSpec({

    describe("IndicatorType") {
        it("dslCode 라운드트립 변환") {
            IndicatorType.entries.forEach { type ->
                IndicatorType.fromDslCode(type.dslCode) shouldBe type
            }
        }

        it("Python DSL과 코드 호환") {
            IndicatorType.fromDslCode("sma") shouldBe IndicatorType.SMA
            IndicatorType.fromDslCode("ema") shouldBe IndicatorType.EMA
            IndicatorType.fromDslCode("rsi") shouldBe IndicatorType.RSI
            IndicatorType.fromDslCode("macd") shouldBe IndicatorType.MACD
            IndicatorType.fromDslCode("macd_signal") shouldBe IndicatorType.MACD_SIGNAL
            IndicatorType.fromDslCode("macd_hist") shouldBe IndicatorType.MACD_HIST
            IndicatorType.fromDslCode("bollinger_upper") shouldBe IndicatorType.BOLLINGER_UPPER
            IndicatorType.fromDslCode("volume") shouldBe IndicatorType.VOLUME
            IndicatorType.fromDslCode("price") shouldBe IndicatorType.PRICE
        }

        it("SCRUM-166 추가 지표 코드 확인") {
            IndicatorType.fromDslCode("per") shouldBe IndicatorType.PER
            IndicatorType.fromDslCode("pbr") shouldBe IndicatorType.PBR
            IndicatorType.fromDslCode("dividend_yield") shouldBe IndicatorType.DIVIDEND_YIELD
        }

        it("펀더멘탈 지표 코드 확인") {
            IndicatorType.fromDslCode("roe") shouldBe IndicatorType.ROE
            IndicatorType.fromDslCode("earnings_growth") shouldBe IndicatorType.EARNINGS_GROWTH
            IndicatorType.fromDslCode("debt_to_equity") shouldBe IndicatorType.DEBT_TO_EQUITY
            IndicatorType.fromDslCode("forward_pe") shouldBe IndicatorType.FORWARD_PE
        }

        it("isFundamentalIndicator 판별") {
            IndicatorType.isFundamentalIndicator("per") shouldBe true
            IndicatorType.isFundamentalIndicator("pbr") shouldBe true
            IndicatorType.isFundamentalIndicator("dividend_yield") shouldBe true
            IndicatorType.isFundamentalIndicator("roe") shouldBe true
            IndicatorType.isFundamentalIndicator("earnings_growth") shouldBe true
            IndicatorType.isFundamentalIndicator("debt_to_equity") shouldBe true
            IndicatorType.isFundamentalIndicator("forward_pe") shouldBe true
            IndicatorType.isFundamentalIndicator("sma") shouldBe false
            IndicatorType.isFundamentalIndicator("rsi") shouldBe false
        }

        it("잘못된 코드로 변환 시 예외") {
            shouldThrow<IllegalArgumentException> {
                IndicatorType.fromDslCode("invalid")
            }
        }
    }

    describe("ConditionOperator") {
        it("dslCode 라운드트립 변환") {
            ConditionOperator.entries.forEach { op ->
                ConditionOperator.fromDslCode(op.dslCode) shouldBe op
            }
        }

        it("Python DSL과 코드 호환") {
            ConditionOperator.fromDslCode("gt") shouldBe ConditionOperator.GT
            ConditionOperator.fromDslCode("gte") shouldBe ConditionOperator.GTE
            ConditionOperator.fromDslCode("lt") shouldBe ConditionOperator.LT
            ConditionOperator.fromDslCode("lte") shouldBe ConditionOperator.LTE
            ConditionOperator.fromDslCode("eq") shouldBe ConditionOperator.EQ
            ConditionOperator.fromDslCode("neq") shouldBe ConditionOperator.NEQ
            ConditionOperator.fromDslCode("crosses_above") shouldBe ConditionOperator.CROSSES_ABOVE
            ConditionOperator.fromDslCode("crosses_below") shouldBe ConditionOperator.CROSSES_BELOW
        }

        it("cross 연산자 판별") {
            ConditionOperator.CROSSES_ABOVE.isCrossOperator() shouldBe true
            ConditionOperator.CROSSES_BELOW.isCrossOperator() shouldBe true
            ConditionOperator.GT.isCrossOperator() shouldBe false
            ConditionOperator.LT.isCrossOperator() shouldBe false
        }

        it("잘못된 코드로 변환 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionOperator.fromDslCode("unknown")
            }
        }
    }

    describe("SignalType") {
        it("dslCode 라운드트립 변환") {
            SignalType.entries.forEach { signal ->
                SignalType.fromDslCode(signal.dslCode) shouldBe signal
            }
        }

        it("Python DSL과 코드 호환") {
            SignalType.fromDslCode("buy") shouldBe SignalType.BUY
            SignalType.fromDslCode("sell") shouldBe SignalType.SELL
            SignalType.fromDslCode("hold") shouldBe SignalType.HOLD
        }

        it("잘못된 코드로 변환 시 예외") {
            shouldThrow<IllegalArgumentException> {
                SignalType.fromDslCode("invalid")
            }
        }
    }

    describe("ConditionLogic") {
        it("dslCode 라운드트립 변환") {
            ConditionLogic.entries.forEach { logic ->
                ConditionLogic.fromDslCode(logic.dslCode) shouldBe logic
            }
        }

        it("Python DSL과 코드 호환") {
            ConditionLogic.fromDslCode("and") shouldBe ConditionLogic.AND
            ConditionLogic.fromDslCode("or") shouldBe ConditionLogic.OR
        }

        it("잘못된 코드로 변환 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionLogic.fromDslCode("xor")
            }
        }
    }
})
