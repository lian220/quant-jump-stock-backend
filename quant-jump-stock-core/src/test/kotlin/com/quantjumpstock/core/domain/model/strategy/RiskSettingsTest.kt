package com.quantjumpstock.core.domain.model.strategy

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class RiskSettingsTest : DescribeSpec({

    describe("RiskSettings") {
        it("기본값으로 생성") {
            val rs = RiskSettings.DEFAULT

            rs.stopLoss.enabled shouldBe false
            rs.stopLoss.value shouldBe -10.0
            rs.takeProfit.enabled shouldBe false
            rs.takeProfit.value shouldBe 20.0
            rs.trailingStop.enabled shouldBe false
            rs.trailingStop.activation shouldBe 5.0
            rs.trailingStop.trail shouldBe 3.0
            rs.timeStop.enabled shouldBe false
            rs.timeStop.maxHoldingDays shouldBe 60
        }
    }

    describe("StopLossConfig") {
        it("유효한 범위 (-100 ~ 0) 허용") {
            StopLossConfig(enabled = true, value = -50.0)
            StopLossConfig(enabled = false, value = -100.0)
            StopLossConfig(enabled = true, value = 0.0)
        }

        it("경계값 -100, 0 허용") {
            val min = StopLossConfig(value = -100.0)
            val max = StopLossConfig(value = 0.0)

            min.value shouldBe -100.0
            max.value shouldBe 0.0
        }

        it("양수 값 시 예외") {
            shouldThrow<IllegalArgumentException> {
                StopLossConfig(value = 0.1)
            }
        }

        it("-100 미만 시 예외") {
            shouldThrow<IllegalArgumentException> {
                StopLossConfig(value = -100.1)
            }
        }
    }

    describe("TakeProfitConfig") {
        it("유효한 범위 (0 ~ 1000) 허용") {
            TakeProfitConfig(enabled = true, value = 50.0)
            TakeProfitConfig(enabled = false, value = 0.0)
            TakeProfitConfig(enabled = true, value = 1000.0)
        }

        it("경계값 0, 1000 허용") {
            val min = TakeProfitConfig(value = 0.0)
            val max = TakeProfitConfig(value = 1000.0)

            min.value shouldBe 0.0
            max.value shouldBe 1000.0
        }

        it("음수 값 시 예외") {
            shouldThrow<IllegalArgumentException> {
                TakeProfitConfig(value = -0.1)
            }
        }

        it("1000 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                TakeProfitConfig(value = 1000.1)
            }
        }
    }

    describe("TrailingStopConfig") {
        it("유효한 값으로 생성") {
            val ts = TrailingStopConfig(enabled = true, activation = 10.0, trail = 5.0)

            ts.activation shouldBe 10.0
            ts.trail shouldBe 5.0
        }

        it("trail >= activation 시 예외") {
            shouldThrow<IllegalArgumentException> {
                TrailingStopConfig(activation = 5.0, trail = 5.0)
            }
            shouldThrow<IllegalArgumentException> {
                TrailingStopConfig(activation = 5.0, trail = 6.0)
            }
        }

        it("activation 음수 시 예외") {
            shouldThrow<IllegalArgumentException> {
                TrailingStopConfig(activation = -1.0, trail = 1.0)
            }
        }

        it("activation 0 시 예외") {
            shouldThrow<IllegalArgumentException> {
                TrailingStopConfig(activation = 0.0, trail = 1.0)
            }
        }

        it("trail 음수 시 예외") {
            shouldThrow<IllegalArgumentException> {
                TrailingStopConfig(activation = 5.0, trail = -1.0)
            }
        }

        it("trail 0 시 예외") {
            shouldThrow<IllegalArgumentException> {
                TrailingStopConfig(activation = 5.0, trail = 0.0)
            }
        }
    }

    describe("TimeStopConfig") {
        it("유효한 범위 (1 ~ 365) 허용") {
            TimeStopConfig(enabled = true, maxHoldingDays = 1)
            TimeStopConfig(enabled = true, maxHoldingDays = 180)
            TimeStopConfig(enabled = true, maxHoldingDays = 365)
        }

        it("경계값 1, 365 허용") {
            val min = TimeStopConfig(maxHoldingDays = 1)
            val max = TimeStopConfig(maxHoldingDays = 365)

            min.maxHoldingDays shouldBe 1
            max.maxHoldingDays shouldBe 365
        }

        it("0 이하 시 예외") {
            shouldThrow<IllegalArgumentException> {
                TimeStopConfig(maxHoldingDays = 0)
            }
            shouldThrow<IllegalArgumentException> {
                TimeStopConfig(maxHoldingDays = -1)
            }
        }

        it("365 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                TimeStopConfig(maxHoldingDays = 366)
            }
        }
    }
})
