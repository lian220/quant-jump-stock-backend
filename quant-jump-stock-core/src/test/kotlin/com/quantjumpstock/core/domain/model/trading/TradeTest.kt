package com.quantjumpstock.core.domain.model.trading

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal

class TradeTest : DescribeSpec({

    describe("Trade 생성") {
        it("매수 주문 생성") {
            val trade = Trade.createBuyOrder(
                userId = 1L,
                ticker = "AAPL",
                quantity = 10,
                price = BigDecimal("150.00")
            )

            trade.side shouldBe TradeSide.BUY
            trade.ticker shouldBe "AAPL"
            trade.quantity shouldBe 10
            trade.price shouldBe BigDecimal("150.00")
            trade.totalAmount shouldBe BigDecimal("1500.00")
            trade.status shouldBe TradeStatus.PENDING
        }

        it("매도 주문 생성") {
            val trade = Trade.createSellOrder(
                userId = 1L,
                ticker = "AAPL",
                quantity = 5,
                price = BigDecimal("160.00")
            )

            trade.side shouldBe TradeSide.SELL
            trade.totalAmount shouldBe BigDecimal("800.00")
        }

        it("빈 티커로 생성 시 예외") {
            shouldThrow<IllegalArgumentException> {
                Trade.createBuyOrder(
                    userId = 1L,
                    ticker = "",
                    quantity = 10,
                    price = BigDecimal("150.00")
                )
            }
        }

        it("0 이하 수량으로 생성 시 예외") {
            shouldThrow<IllegalArgumentException> {
                Trade.createBuyOrder(
                    userId = 1L,
                    ticker = "AAPL",
                    quantity = 0,
                    price = BigDecimal("150.00")
                )
            }
        }

        it("0 이하 가격으로 생성 시 예외") {
            shouldThrow<IllegalArgumentException> {
                Trade.createBuyOrder(
                    userId = 1L,
                    ticker = "AAPL",
                    quantity = 10,
                    price = BigDecimal.ZERO
                )
            }
        }
    }

    describe("Trade 상태 전이") {
        it("PENDING → EXECUTED 전이 가능") {
            val trade = Trade.createBuyOrder(
                userId = 1L,
                ticker = "AAPL",
                quantity = 10,
                price = BigDecimal("150.00")
            )
            val executed = trade.execute("KIS-ORDER-001")

            executed.status shouldBe TradeStatus.EXECUTED
            executed.kisOrderId shouldBe "KIS-ORDER-001"
            executed.executedAt shouldNotBe null
        }

        it("PENDING → FAILED 전이 가능") {
            val trade = Trade.createBuyOrder(
                userId = 1L,
                ticker = "AAPL",
                quantity = 10,
                price = BigDecimal("150.00")
            )
            val failed = trade.fail()

            failed.status shouldBe TradeStatus.FAILED
        }

        it("PENDING → CANCELLED 전이 가능") {
            val trade = Trade.createBuyOrder(
                userId = 1L,
                ticker = "AAPL",
                quantity = 10,
                price = BigDecimal("150.00")
            )
            val cancelled = trade.cancel()

            cancelled.status shouldBe TradeStatus.CANCELLED
        }

        it("EXECUTED 상태에서 다른 상태로 전이 불가") {
            val trade = Trade.createBuyOrder(
                userId = 1L,
                ticker = "AAPL",
                quantity = 10,
                price = BigDecimal("150.00")
            ).execute()

            shouldThrow<IllegalArgumentException> {
                trade.cancel()
            }
        }
    }

    describe("Trade 순거래금액 계산") {
        it("매수 시 순거래금액 = 총액 + 수수료") {
            val trade = Trade.createBuyOrder(
                userId = 1L,
                ticker = "AAPL",
                quantity = 10,
                price = BigDecimal("150.00"),
                commission = BigDecimal("10.00")
            )

            trade.netAmount() shouldBe BigDecimal("1510.00")
        }

        it("매도 시 순거래금액 = 총액 - 수수료") {
            val trade = Trade.createSellOrder(
                userId = 1L,
                ticker = "AAPL",
                quantity = 10,
                price = BigDecimal("150.00"),
                commission = BigDecimal("10.00")
            )

            trade.netAmount() shouldBe BigDecimal("1490.00")
        }
    }

    describe("Trade 최종 상태 확인") {
        it("PENDING은 최종 상태 아님") {
            val trade = Trade.createBuyOrder(
                userId = 1L,
                ticker = "AAPL",
                quantity = 10,
                price = BigDecimal("150.00")
            )

            trade.isFinal() shouldBe false
        }

        it("EXECUTED는 최종 상태") {
            val trade = Trade.createBuyOrder(
                userId = 1L,
                ticker = "AAPL",
                quantity = 10,
                price = BigDecimal("150.00")
            ).execute()

            trade.isFinal() shouldBe true
        }

        it("FAILED는 최종 상태") {
            val trade = Trade.createBuyOrder(
                userId = 1L,
                ticker = "AAPL",
                quantity = 10,
                price = BigDecimal("150.00")
            ).fail()

            trade.isFinal() shouldBe true
        }
    }

    describe("Trade 불변성") {
        it("체결 시 새 객체 반환") {
            val original = Trade.createBuyOrder(
                userId = 1L,
                ticker = "AAPL",
                quantity = 10,
                price = BigDecimal("150.00")
            )
            val executed = original.execute()

            original shouldNotBe executed
            original.status shouldBe TradeStatus.PENDING
            executed.status shouldBe TradeStatus.EXECUTED
        }
    }
})
