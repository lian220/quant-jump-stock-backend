package com.quantjumpstock.core.domain.model.strategy

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class TradingCostsTest : DescribeSpec({

    describe("TradingCosts") {
        it("기본값으로 생성") {
            val tc = TradingCosts.DEFAULT

            tc.commission.rate shouldBe 0.015
            tc.commission.minFee shouldBe 0.0
            tc.tax.rate shouldBe 0.23
            tc.tax.appliesTo shouldBe TaxAppliesTo.SELL
            tc.slippage.model shouldBe SlippageModel.ADAPTIVE
            tc.slippage.fixedBps shouldBe 5
            tc.slippage.volumeImpact shouldBe 0.1
        }

        it("커스텀 값으로 생성") {
            val tc = TradingCosts(
                commission = CommissionConfig(rate = 0.01, minFee = 1.0),
                tax = TaxConfig(rate = 0.1, appliesTo = TaxAppliesTo.BOTH),
                slippage = SlippageConfig(model = SlippageModel.FIXED, fixedBps = 10, volumeImpact = 0.0)
            )

            tc.commission.rate shouldBe 0.01
            tc.commission.minFee shouldBe 1.0
            tc.tax.rate shouldBe 0.1
            tc.tax.appliesTo shouldBe TaxAppliesTo.BOTH
            tc.slippage.model shouldBe SlippageModel.FIXED
            tc.slippage.fixedBps shouldBe 10
        }
    }

    describe("CommissionConfig 검증") {
        it("경계값 0, 1.0 허용") {
            val min = CommissionConfig(rate = 0.0)
            val max = CommissionConfig(rate = 1.0)

            min.rate shouldBe 0.0
            max.rate shouldBe 1.0
        }

        it("rate 음수 시 예외") {
            shouldThrow<IllegalArgumentException> {
                CommissionConfig(rate = -0.01)
            }
        }

        it("rate 1.0 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                CommissionConfig(rate = 1.01)
            }
        }

        it("minFee 음수 시 예외") {
            shouldThrow<IllegalArgumentException> {
                CommissionConfig(minFee = -1.0)
            }
        }
    }

    describe("TaxConfig 검증") {
        it("경계값 0, 1.0 허용") {
            val min = TaxConfig(rate = 0.0)
            val max = TaxConfig(rate = 1.0)

            min.rate shouldBe 0.0
            max.rate shouldBe 1.0
        }

        it("rate 음수 시 예외") {
            shouldThrow<IllegalArgumentException> {
                TaxConfig(rate = -0.01)
            }
        }

        it("rate 1.0 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                TaxConfig(rate = 1.01)
            }
        }
    }

    describe("SlippageConfig 검증") {
        it("경계값 fixedBps 0, 100 허용") {
            val min = SlippageConfig(fixedBps = 0)
            val max = SlippageConfig(fixedBps = 100)

            min.fixedBps shouldBe 0
            max.fixedBps shouldBe 100
        }

        it("fixedBps 음수 시 예외") {
            shouldThrow<IllegalArgumentException> {
                SlippageConfig(fixedBps = -1)
            }
        }

        it("fixedBps 100 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                SlippageConfig(fixedBps = 101)
            }
        }

        it("경계값 volumeImpact 0, 1.0 허용") {
            val min = SlippageConfig(volumeImpact = 0.0)
            val max = SlippageConfig(volumeImpact = 1.0)

            min.volumeImpact shouldBe 0.0
            max.volumeImpact shouldBe 1.0
        }

        it("volumeImpact 음수 시 예외") {
            shouldThrow<IllegalArgumentException> {
                SlippageConfig(volumeImpact = -0.01)
            }
        }

        it("volumeImpact 1.0 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                SlippageConfig(volumeImpact = 1.01)
            }
        }
    }

    describe("SlippageModel") {
        it("fromString 변환") {
            SlippageModel.fromString("none") shouldBe SlippageModel.NONE
            SlippageModel.fromString("fixed") shouldBe SlippageModel.FIXED
            SlippageModel.fromString("adaptive") shouldBe SlippageModel.ADAPTIVE
        }

        it("대소문자 무관") {
            SlippageModel.fromString("NONE") shouldBe SlippageModel.NONE
            SlippageModel.fromString("Fixed") shouldBe SlippageModel.FIXED
        }

        it("알 수 없는 model 시 예외") {
            shouldThrow<IllegalArgumentException> {
                SlippageModel.fromString("unknown")
            }
        }
    }

    describe("TaxAppliesTo") {
        it("fromString 변환") {
            TaxAppliesTo.fromString("buy") shouldBe TaxAppliesTo.BUY
            TaxAppliesTo.fromString("sell") shouldBe TaxAppliesTo.SELL
            TaxAppliesTo.fromString("both") shouldBe TaxAppliesTo.BOTH
        }

        it("대소문자 무관") {
            TaxAppliesTo.fromString("SELL") shouldBe TaxAppliesTo.SELL
            TaxAppliesTo.fromString("Both") shouldBe TaxAppliesTo.BOTH
        }

        it("알 수 없는 값 시 예외") {
            shouldThrow<IllegalArgumentException> {
                TaxAppliesTo.fromString("unknown")
            }
        }
    }
})
