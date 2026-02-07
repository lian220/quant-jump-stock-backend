package com.quantjumpstock.core.domain.model.strategy

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PositionSizingTest : DescribeSpec({

    describe("PositionSizing") {
        it("기본값으로 생성") {
            val ps = PositionSizing.DEFAULT

            ps.method shouldBe PositionSizingMethod.FIXED_PERCENTAGE
            ps.maxPositionPct shouldBe 10.0
            ps.maxPositions shouldBe 10
            ps.volatilityScaling shouldBe false
            ps.lookbackDays shouldBe 20
            ps.targetVolatility shouldBe 0.15
            ps.kellyFraction shouldBe 0.5
        }

        it("커스텀 값으로 생성") {
            val ps = PositionSizing(
                method = PositionSizingMethod.VOLATILITY_ADJUSTED,
                maxPositionPct = 20.0,
                maxPositions = 5,
                volatilityScaling = true,
                lookbackDays = 60,
                targetVolatility = 0.2,
                kellyFraction = 0.8
            )

            ps.method shouldBe PositionSizingMethod.VOLATILITY_ADJUSTED
            ps.maxPositionPct shouldBe 20.0
            ps.maxPositions shouldBe 5
            ps.volatilityScaling shouldBe true
            ps.lookbackDays shouldBe 60
            ps.targetVolatility shouldBe 0.2
            ps.kellyFraction shouldBe 0.8
        }
    }

    describe("maxPositionPct 검증") {
        it("경계값 1, 100 허용") {
            val min = PositionSizing(maxPositionPct = 1.0)
            val max = PositionSizing(maxPositionPct = 100.0)

            min.maxPositionPct shouldBe 1.0
            max.maxPositionPct shouldBe 100.0
        }

        it("1 미만 시 예외") {
            shouldThrow<IllegalArgumentException> {
                PositionSizing(maxPositionPct = 0.9)
            }
        }

        it("100 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                PositionSizing(maxPositionPct = 100.1)
            }
        }
    }

    describe("maxPositions 검증") {
        it("경계값 1, 100 허용") {
            val min = PositionSizing(maxPositions = 1)
            val max = PositionSizing(maxPositions = 100)

            min.maxPositions shouldBe 1
            max.maxPositions shouldBe 100
        }

        it("0 이하 시 예외") {
            shouldThrow<IllegalArgumentException> {
                PositionSizing(maxPositions = 0)
            }
        }

        it("100 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                PositionSizing(maxPositions = 101)
            }
        }
    }

    describe("targetVolatility 검증") {
        it("경계값 0.01, 1.0 허용") {
            val min = PositionSizing(targetVolatility = 0.01)
            val max = PositionSizing(targetVolatility = 1.0)

            min.targetVolatility shouldBe 0.01
            max.targetVolatility shouldBe 1.0
        }

        it("0.01 미만 시 예외") {
            shouldThrow<IllegalArgumentException> {
                PositionSizing(targetVolatility = 0.009)
            }
        }

        it("1.0 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                PositionSizing(targetVolatility = 1.01)
            }
        }
    }

    describe("lookbackDays 검증") {
        it("경계값 5, 252 허용") {
            val min = PositionSizing(lookbackDays = 5)
            val max = PositionSizing(lookbackDays = 252)

            min.lookbackDays shouldBe 5
            max.lookbackDays shouldBe 252
        }

        it("5 미만 시 예외") {
            shouldThrow<IllegalArgumentException> {
                PositionSizing(lookbackDays = 4)
            }
        }

        it("252 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                PositionSizing(lookbackDays = 253)
            }
        }
    }

    describe("kellyFraction 검증") {
        it("경계값 0.1, 1.0 허용") {
            val min = PositionSizing(kellyFraction = 0.1)
            val max = PositionSizing(kellyFraction = 1.0)

            min.kellyFraction shouldBe 0.1
            max.kellyFraction shouldBe 1.0
        }

        it("0.1 미만 시 예외") {
            shouldThrow<IllegalArgumentException> {
                PositionSizing(kellyFraction = 0.09)
            }
        }

        it("1.0 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                PositionSizing(kellyFraction = 1.01)
            }
        }
    }

    describe("PositionSizingMethod") {
        it("fromString 변환") {
            PositionSizingMethod.fromString("fixed_percentage") shouldBe PositionSizingMethod.FIXED_PERCENTAGE
            PositionSizingMethod.fromString("volatility_adjusted") shouldBe PositionSizingMethod.VOLATILITY_ADJUSTED
            PositionSizingMethod.fromString("kelly_criterion") shouldBe PositionSizingMethod.KELLY_CRITERION
        }

        it("대소문자 무관") {
            PositionSizingMethod.fromString("FIXED_PERCENTAGE") shouldBe PositionSizingMethod.FIXED_PERCENTAGE
            PositionSizingMethod.fromString("Kelly_Criterion") shouldBe PositionSizingMethod.KELLY_CRITERION
        }

        it("알 수 없는 method 시 예외") {
            shouldThrow<IllegalArgumentException> {
                PositionSizingMethod.fromString("unknown_method")
            }
        }
    }
})
