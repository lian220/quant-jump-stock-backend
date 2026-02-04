package com.quantjumpstock.core.domain.model.strategy

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.math.BigDecimal

class StrategyTest : DescribeSpec({

    describe("Strategy 생성") {
        it("유효한 파라미터로 생성") {
            val strategy = Strategy(
                name = "Test Strategy",
                categoryId = 1L,
                ownerId = 1L
            )

            strategy.name shouldBe "Test Strategy"
            strategy.status shouldBe StrategyStatus.DRAFT
            strategy.subscriberCount shouldBe 0
        }

        it("빈 이름으로 생성 시 예외") {
            shouldThrow<IllegalArgumentException> {
                Strategy(name = "", categoryId = 1L)
            }
        }

        it("100자 초과 이름으로 생성 시 예외") {
            shouldThrow<IllegalArgumentException> {
                Strategy(name = "a".repeat(101), categoryId = 1L)
            }
        }

        it("음수 구독자 수로 생성 시 예외") {
            shouldThrow<IllegalArgumentException> {
                Strategy(name = "Test", categoryId = 1L, subscriberCount = -1)
            }
        }

        it("범위 초과 평점으로 생성 시 예외") {
            shouldThrow<IllegalArgumentException> {
                Strategy(name = "Test", categoryId = 1L, averageRating = BigDecimal("5.1"))
            }
        }
    }

    describe("Strategy 상태 전이") {
        it("DRAFT → PENDING_REVIEW 전이 가능") {
            val draft = Strategy(name = "Test", categoryId = 1L, status = StrategyStatus.DRAFT)
            val pending = draft.submitForReview()

            pending.status shouldBe StrategyStatus.PENDING_REVIEW
        }

        it("PENDING_REVIEW → APPROVED 전이 가능") {
            val pending = Strategy(name = "Test", categoryId = 1L, status = StrategyStatus.PENDING_REVIEW)
            val approved = pending.approve()

            approved.status shouldBe StrategyStatus.APPROVED
        }

        it("APPROVED → PUBLISHED 전이 가능") {
            val approved = Strategy(name = "Test", categoryId = 1L, status = StrategyStatus.APPROVED)
            val published = approved.publish()

            published.status shouldBe StrategyStatus.PUBLISHED
        }

        it("DRAFT에서 직접 PUBLISHED 전이 불가") {
            val draft = Strategy(name = "Test", categoryId = 1L, status = StrategyStatus.DRAFT)

            shouldThrow<IllegalArgumentException> {
                draft.publish()
            }
        }

        it("PENDING_REVIEW → REJECTED 전이 가능") {
            val pending = Strategy(name = "Test", categoryId = 1L, status = StrategyStatus.PENDING_REVIEW)
            val rejected = pending.reject()

            rejected.status shouldBe StrategyStatus.REJECTED
        }
    }

    describe("Strategy 구독자 관리") {
        it("구독자 수 증가") {
            val strategy = Strategy(name = "Test", categoryId = 1L, subscriberCount = 0)
            val updated = strategy.incrementSubscriberCount()

            updated.subscriberCount shouldBe 1
        }

        it("구독자 수 감소") {
            val strategy = Strategy(name = "Test", categoryId = 1L, subscriberCount = 1)
            val updated = strategy.decrementSubscriberCount()

            updated.subscriberCount shouldBe 0
        }

        it("0에서 구독자 수 감소 시 예외") {
            val strategy = Strategy(name = "Test", categoryId = 1L, subscriberCount = 0)

            shouldThrow<IllegalArgumentException> {
                strategy.decrementSubscriberCount()
            }
        }
    }

    describe("Strategy 마켓플레이스 노출") {
        it("발행되고 공개된 전략은 마켓플레이스에 노출") {
            val strategy = Strategy(
                name = "Test",
                categoryId = 1L,
                status = StrategyStatus.PUBLISHED,
                isPublic = true
            )

            strategy.isVisibleInMarketplace() shouldBe true
        }

        it("발행되었지만 비공개인 전략은 마켓플레이스에 미노출") {
            val strategy = Strategy(
                name = "Test",
                categoryId = 1L,
                status = StrategyStatus.PUBLISHED,
                isPublic = false
            )

            strategy.isVisibleInMarketplace() shouldBe false
        }

        it("미발행 전략은 마켓플레이스에 미노출") {
            val strategy = Strategy(
                name = "Test",
                categoryId = 1L,
                status = StrategyStatus.APPROVED,
                isPublic = true
            )

            strategy.isVisibleInMarketplace() shouldBe false
        }
    }

    describe("Strategy 불변성") {
        it("상태 변경 시 새 객체 반환") {
            val original = Strategy(name = "Test", categoryId = 1L)
            val updated = original.submitForReview()

            original shouldNotBe updated
            original.status shouldBe StrategyStatus.DRAFT
            updated.status shouldBe StrategyStatus.PENDING_REVIEW
        }
    }
})
