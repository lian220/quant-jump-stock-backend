package com.quantjumpstock.core.domain.model.trading

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal

class AccountTest : DescribeSpec({

    describe("Account 생성") {
        it("새 계좌 생성") {
            val account = Account.createNew(userId = 1L, initialCash = BigDecimal("1000000"))

            account.userId shouldBe 1L
            account.cash shouldBe BigDecimal("1000000")
            account.totalValue shouldBe BigDecimal("1000000")
            account.lockedCash shouldBe BigDecimal.ZERO
        }

        it("음수 현금으로 생성 시 예외") {
            shouldThrow<IllegalArgumentException> {
                Account(userId = 1L, cash = BigDecimal("-100"))
            }
        }

        it("잠긴 현금이 총 현금 초과 시 예외") {
            shouldThrow<IllegalArgumentException> {
                Account(
                    userId = 1L,
                    cash = BigDecimal("1000"),
                    lockedCash = BigDecimal("1500")
                )
            }
        }
    }

    describe("Account 사용 가능 현금") {
        it("사용 가능 현금 = 총 현금 - 잠긴 현금") {
            val account = Account(
                userId = 1L,
                cash = BigDecimal("1000"),
                lockedCash = BigDecimal("300")
            )

            account.availableCash() shouldBe BigDecimal("700")
        }
    }

    describe("Account 입금/출금") {
        it("입금") {
            val account = Account.createNew(userId = 1L, initialCash = BigDecimal("1000"))
            val updated = account.deposit(BigDecimal("500"))

            updated.cash shouldBe BigDecimal("1500")
            updated.totalValue shouldBe BigDecimal("1500")
        }

        it("출금") {
            val account = Account.createNew(userId = 1L, initialCash = BigDecimal("1000"))
            val updated = account.withdraw(BigDecimal("300"))

            updated.cash shouldBe BigDecimal("700")
            updated.totalValue shouldBe BigDecimal("700")
        }

        it("사용 가능 현금 초과 출금 시 예외") {
            val account = Account(
                userId = 1L,
                cash = BigDecimal("1000"),
                lockedCash = BigDecimal("700")
            )

            shouldThrow<IllegalArgumentException> {
                account.withdraw(BigDecimal("500"))  // 사용 가능: 300
            }
        }

        it("0 이하 입금 시 예외") {
            val account = Account.createNew(userId = 1L)

            shouldThrow<IllegalArgumentException> {
                account.deposit(BigDecimal.ZERO)
            }
        }
    }

    describe("Account 현금 잠금/해제") {
        it("주문용 현금 잠금") {
            val account = Account.createNew(userId = 1L, initialCash = BigDecimal("1000"))
            val locked = account.lockCash(BigDecimal("500"))

            locked.lockedCash shouldBe BigDecimal("500")
            locked.availableCash() shouldBe BigDecimal("500")
        }

        it("사용 가능 현금 초과 잠금 시 예외") {
            val account = Account.createNew(userId = 1L, initialCash = BigDecimal("1000"))

            shouldThrow<IllegalArgumentException> {
                account.lockCash(BigDecimal("1500"))
            }
        }

        it("잠긴 현금 해제") {
            val account = Account(
                userId = 1L,
                cash = BigDecimal("1000"),
                lockedCash = BigDecimal("500")
            )
            val unlocked = account.unlockCash(BigDecimal("300"))

            unlocked.lockedCash shouldBe BigDecimal("200")
        }

        it("잠긴 금액 초과 해제 시 예외") {
            val account = Account(
                userId = 1L,
                cash = BigDecimal("1000"),
                lockedCash = BigDecimal("500")
            )

            shouldThrow<IllegalArgumentException> {
                account.unlockCash(BigDecimal("600"))
            }
        }
    }

    describe("Account 거래 체결") {
        it("매수 체결 처리") {
            val account = Account(
                userId = 1L,
                cash = BigDecimal("1000"),
                lockedCash = BigDecimal("500"),
                totalValue = BigDecimal("1000")
            )
            val afterBuy = account.executeBuy(BigDecimal("500"))

            afterBuy.cash shouldBe BigDecimal("500")
            afterBuy.lockedCash shouldBe BigDecimal.ZERO
        }

        it("잠긴 금액 초과 매수 체결 시 예외") {
            val account = Account(
                userId = 1L,
                cash = BigDecimal("1000"),
                lockedCash = BigDecimal("300")
            )

            shouldThrow<IllegalArgumentException> {
                account.executeBuy(BigDecimal("500"))
            }
        }

        it("매도 체결 처리") {
            val account = Account.createNew(userId = 1L, initialCash = BigDecimal("500"))
            val afterSell = account.executeSell(BigDecimal("300"))

            afterSell.cash shouldBe BigDecimal("800")
        }
    }

    describe("Account 주문 가능 여부") {
        it("주문 가능") {
            val account = Account.createNew(userId = 1L, initialCash = BigDecimal("1000"))

            account.canPlaceOrder(BigDecimal("500")) shouldBe true
        }

        it("주문 불가 - 잔액 부족") {
            val account = Account.createNew(userId = 1L, initialCash = BigDecimal("1000"))

            account.canPlaceOrder(BigDecimal("1500")) shouldBe false
        }

        it("주문 불가 - 잠긴 현금 때문에 잔액 부족") {
            val account = Account(
                userId = 1L,
                cash = BigDecimal("1000"),
                lockedCash = BigDecimal("800")
            )

            account.canPlaceOrder(BigDecimal("500")) shouldBe false
        }
    }

    describe("Account 불변성") {
        it("입금 시 새 객체 반환") {
            val original = Account.createNew(userId = 1L, initialCash = BigDecimal("1000"))
            val updated = original.deposit(BigDecimal("500"))

            original shouldNotBe updated
            original.cash shouldBe BigDecimal("1000")
            updated.cash shouldBe BigDecimal("1500")
        }
    }
})
