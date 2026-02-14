package com.quantjumpstock.core.domain.model.strategy

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class ConditionBlockTest : DescribeSpec({

    describe("SmaCrossover") {
        it("유효한 파라미터로 생성") {
            val block = ConditionBlock.SmaCrossover(
                shortPeriod = 20,
                longPeriod = 50
            )

            block.type shouldBe "SMA_CROSSOVER"
            block.shortPeriod shouldBe 20
            block.longPeriod shouldBe 50
            block.movingAverageType shouldBe IndicatorType.SMA
            block.crossDirection shouldBe ConditionOperator.CROSSES_ABOVE
        }

        it("EMA 타입으로 생성") {
            val block = ConditionBlock.SmaCrossover(
                shortPeriod = 12,
                longPeriod = 26,
                movingAverageType = IndicatorType.EMA,
                crossDirection = ConditionOperator.CROSSES_BELOW
            )

            block.movingAverageType shouldBe IndicatorType.EMA
            block.crossDirection shouldBe ConditionOperator.CROSSES_BELOW
        }

        it("shortPeriod >= longPeriod 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.SmaCrossover(shortPeriod = 50, longPeriod = 20)
            }
        }

        it("shortPeriod == longPeriod 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.SmaCrossover(shortPeriod = 20, longPeriod = 20)
            }
        }

        it("잘못된 이동평균 타입 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.SmaCrossover(
                    shortPeriod = 20,
                    longPeriod = 50,
                    movingAverageType = IndicatorType.RSI
                )
            }
        }

        it("non-cross 연산자 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.SmaCrossover(
                    shortPeriod = 20,
                    longPeriod = 50,
                    crossDirection = ConditionOperator.GT
                )
            }
        }

        it("0 이하 period 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.SmaCrossover(shortPeriod = 0, longPeriod = 50)
            }
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.SmaCrossover(shortPeriod = 5, longPeriod = -1)
            }
        }
    }

    describe("RsiFilter") {
        it("유효한 파라미터로 생성") {
            val block = ConditionBlock.RsiFilter(
                operator = ConditionOperator.LT,
                threshold = BigDecimal("30")
            )

            block.type shouldBe "RSI_FILTER"
            block.period shouldBe 14
            block.operator shouldBe ConditionOperator.LT
            block.threshold shouldBe BigDecimal("30")
        }

        it("경계값 0, 100 허용") {
            ConditionBlock.RsiFilter(
                operator = ConditionOperator.GTE,
                threshold = BigDecimal.ZERO
            )
            ConditionBlock.RsiFilter(
                operator = ConditionOperator.LTE,
                threshold = BigDecimal("100")
            )
        }

        it("threshold 범위 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.RsiFilter(
                    operator = ConditionOperator.LT,
                    threshold = BigDecimal("-1")
                )
            }
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.RsiFilter(
                    operator = ConditionOperator.GT,
                    threshold = BigDecimal("101")
                )
            }
        }

        it("cross 연산자 사용 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.RsiFilter(
                    operator = ConditionOperator.CROSSES_ABOVE,
                    threshold = BigDecimal("30")
                )
            }
        }
    }

    describe("MacdSignal") {
        it("유효한 파라미터로 생성") {
            val block = ConditionBlock.MacdSignal()

            block.type shouldBe "MACD_SIGNAL"
            block.shortPeriod shouldBe 12
            block.longPeriod shouldBe 26
            block.signalPeriod shouldBe 9
            block.crossDirection shouldBe ConditionOperator.CROSSES_ABOVE
        }

        it("커스텀 파라미터로 생성") {
            val block = ConditionBlock.MacdSignal(
                shortPeriod = 8,
                longPeriod = 21,
                signalPeriod = 5,
                crossDirection = ConditionOperator.CROSSES_BELOW
            )

            block.shortPeriod shouldBe 8
            block.longPeriod shouldBe 21
            block.signalPeriod shouldBe 5
        }

        it("shortPeriod >= longPeriod 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.MacdSignal(shortPeriod = 26, longPeriod = 12)
            }
        }

        it("non-cross 연산자 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.MacdSignal(crossDirection = ConditionOperator.GT)
            }
        }

        it("0 이하 signalPeriod 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.MacdSignal(signalPeriod = 0)
            }
        }
    }

    describe("PerFilter") {
        it("유효한 파라미터로 생성") {
            val block = ConditionBlock.PerFilter(
                operator = ConditionOperator.LT,
                threshold = BigDecimal("15")
            )

            block.type shouldBe "PER_FILTER"
            block.operator shouldBe ConditionOperator.LT
            block.threshold shouldBe BigDecimal("15")
        }

        it("threshold 0 허용 (수익 있는 기업 필터)") {
            val block = ConditionBlock.PerFilter(
                operator = ConditionOperator.GT,
                threshold = BigDecimal.ZERO
            )
            block.threshold shouldBe BigDecimal.ZERO
        }

        it("threshold 음수 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.PerFilter(
                    operator = ConditionOperator.LT,
                    threshold = BigDecimal("-5")
                )
            }
        }

        it("cross 연산자 사용 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.PerFilter(
                    operator = ConditionOperator.CROSSES_BELOW,
                    threshold = BigDecimal("15")
                )
            }
        }
    }

    describe("PbrFilter") {
        it("유효한 파라미터로 생성") {
            val block = ConditionBlock.PbrFilter(
                operator = ConditionOperator.LT,
                threshold = BigDecimal("1.5")
            )

            block.type shouldBe "PBR_FILTER"
            block.operator shouldBe ConditionOperator.LT
            block.threshold shouldBe BigDecimal("1.5")
        }

        it("threshold 0 허용") {
            val block = ConditionBlock.PbrFilter(
                operator = ConditionOperator.GT,
                threshold = BigDecimal.ZERO
            )
            block.threshold shouldBe BigDecimal.ZERO
        }

        it("threshold 음수 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.PbrFilter(
                    operator = ConditionOperator.LT,
                    threshold = BigDecimal("-1")
                )
            }
        }

        it("cross 연산자 사용 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.PbrFilter(
                    operator = ConditionOperator.CROSSES_ABOVE,
                    threshold = BigDecimal("1.5")
                )
            }
        }
    }

    describe("DividendYield") {
        it("유효한 파라미터로 생성") {
            val block = ConditionBlock.DividendYield(
                operator = ConditionOperator.GTE,
                threshold = BigDecimal("3")
            )

            block.type shouldBe "DIVIDEND_YIELD"
            block.operator shouldBe ConditionOperator.GTE
            block.threshold shouldBe BigDecimal("3")
        }

        it("경계값 0, 100 허용") {
            ConditionBlock.DividendYield(
                operator = ConditionOperator.GTE,
                threshold = BigDecimal.ZERO
            )
            ConditionBlock.DividendYield(
                operator = ConditionOperator.LTE,
                threshold = BigDecimal("100")
            )
        }

        it("threshold 범위 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.DividendYield(
                    operator = ConditionOperator.GTE,
                    threshold = BigDecimal("-1")
                )
            }
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.DividendYield(
                    operator = ConditionOperator.LTE,
                    threshold = BigDecimal("101")
                )
            }
        }

        it("cross 연산자 사용 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.DividendYield(
                    operator = ConditionOperator.CROSSES_ABOVE,
                    threshold = BigDecimal("3")
                )
            }
        }
    }

    describe("Allocation") {
        it("유효한 파라미터로 생성") {
            val block = ConditionBlock.Allocation(
                targetTicker = "AAPL",
                targetWeightPct = BigDecimal("30")
            )

            block.type shouldBe "ALLOCATION"
            block.targetTicker shouldBe "AAPL"
            block.targetWeightPct shouldBe BigDecimal("30")
            block.tolerance shouldBe BigDecimal("5.0")
        }

        it("targetTicker 없이 생성") {
            val block = ConditionBlock.Allocation(
                targetWeightPct = BigDecimal("100")
            )

            block.targetTicker shouldBe null
        }

        it("경계값 0, 100 허용") {
            ConditionBlock.Allocation(targetWeightPct = BigDecimal.ZERO)
            ConditionBlock.Allocation(targetWeightPct = BigDecimal("100"))
        }

        it("targetWeightPct 범위 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.Allocation(targetWeightPct = BigDecimal("-1"))
            }
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.Allocation(targetWeightPct = BigDecimal("101"))
            }
        }

        it("음수 tolerance 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.Allocation(
                    targetWeightPct = BigDecimal("30"),
                    tolerance = BigDecimal("-1")
                )
            }
        }
    }

    describe("FundamentalFilter") {
        it("ROE 필터 생성") {
            val block = ConditionBlock.FundamentalFilter(
                indicator = IndicatorType.ROE,
                operator = ConditionOperator.GT,
                threshold = BigDecimal("15")
            )

            block.type shouldBe "FUNDAMENTAL_FILTER"
            block.indicator shouldBe IndicatorType.ROE
            block.operator shouldBe ConditionOperator.GT
            block.threshold shouldBe BigDecimal("15")
        }

        it("earnings_growth 필터 생성") {
            val block = ConditionBlock.FundamentalFilter(
                indicator = IndicatorType.EARNINGS_GROWTH,
                operator = ConditionOperator.GTE,
                threshold = BigDecimal("10")
            )

            block.indicator shouldBe IndicatorType.EARNINGS_GROWTH
        }

        it("debt_to_equity 필터 생성") {
            val block = ConditionBlock.FundamentalFilter(
                indicator = IndicatorType.DEBT_TO_EQUITY,
                operator = ConditionOperator.LT,
                threshold = BigDecimal("100")
            )

            block.indicator shouldBe IndicatorType.DEBT_TO_EQUITY
        }

        it("forward_pe 필터 생성") {
            val block = ConditionBlock.FundamentalFilter(
                indicator = IndicatorType.FORWARD_PE,
                operator = ConditionOperator.LTE,
                threshold = BigDecimal("20")
            )

            block.indicator shouldBe IndicatorType.FORWARD_PE
        }

        it("비펀더멘탈 지표로 생성 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.FundamentalFilter(
                    indicator = IndicatorType.SMA,
                    operator = ConditionOperator.GT,
                    threshold = BigDecimal("15")
                )
            }
        }

        it("cross 연산자 사용 시 예외") {
            shouldThrow<IllegalArgumentException> {
                ConditionBlock.FundamentalFilter(
                    indicator = IndicatorType.ROE,
                    operator = ConditionOperator.CROSSES_ABOVE,
                    threshold = BigDecimal("15")
                )
            }
        }
    }

    describe("ConditionBlock sealed class") {
        it("모든 서브클래스의 type이 고유") {
            val types = listOf(
                ConditionBlock.SmaCrossover(shortPeriod = 20, longPeriod = 50),
                ConditionBlock.RsiFilter(operator = ConditionOperator.LT, threshold = BigDecimal("30")),
                ConditionBlock.MacdSignal(),
                ConditionBlock.PerFilter(operator = ConditionOperator.LT, threshold = BigDecimal("15")),
                ConditionBlock.PbrFilter(operator = ConditionOperator.LT, threshold = BigDecimal("1.5")),
                ConditionBlock.DividendYield(operator = ConditionOperator.GTE, threshold = BigDecimal("3")),
                ConditionBlock.FundamentalFilter(indicator = IndicatorType.ROE, operator = ConditionOperator.GT, threshold = BigDecimal("15")),
                ConditionBlock.Allocation(targetWeightPct = BigDecimal("30"))
            ).map { it.type }

            types.toSet().size shouldBe 8
        }
    }
})
