package com.quantjumpstock.core.domain.model.strategy

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Strategy 도메인 모델 - 순수 Kotlin
 *
 * 특징:
 * - JPA/MongoDB 어노테이션 없음
 * - 비즈니스 로직 포함
 * - init 블록에서 불변 규칙 검증
 */
data class Strategy(
    val id: Long? = null,
    val name: String,
    val description: String? = null,
    val categoryId: Long,
    val ownerId: Long? = null,
    val isPublic: Boolean = false,
    val isPremium: Boolean = false,
    val status: StrategyStatus = StrategyStatus.DRAFT,
    val conditions: String = "{}",
    val rebalanceFrequency: RebalanceFrequency = RebalanceFrequency.MONTHLY,
    val subscriberCount: Int = 0,
    val averageRating: BigDecimal = BigDecimal.ZERO,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    init {
        require(name.isNotBlank()) { "Strategy name must not be blank" }
        require(name.length in 1..100) { "Strategy name must be 1-100 characters" }
        require(subscriberCount >= 0) { "Subscriber count must be non-negative" }
        require(averageRating >= BigDecimal.ZERO && averageRating <= BigDecimal.valueOf(5)) {
            "Average rating must be between 0 and 5"
        }
    }

    /**
     * 전략 상태 변경 - 비즈니스 규칙 검증
     */
    fun transitionTo(newStatus: StrategyStatus): Strategy {
        require(status.canTransitionTo(newStatus)) {
            "Cannot transition from $status to $newStatus"
        }
        return copy(status = newStatus, updatedAt = LocalDateTime.now())
    }

    /**
     * 검토 제출
     */
    fun submitForReview(): Strategy {
        require(status == StrategyStatus.DRAFT) { "Only draft strategies can be submitted" }
        return transitionTo(StrategyStatus.PENDING_REVIEW)
    }

    /**
     * 승인
     */
    fun approve(): Strategy {
        require(status == StrategyStatus.PENDING_REVIEW) { "Only pending strategies can be approved" }
        return transitionTo(StrategyStatus.APPROVED)
    }

    /**
     * 발행
     */
    fun publish(): Strategy {
        require(status == StrategyStatus.APPROVED) { "Only approved strategies can be published" }
        return transitionTo(StrategyStatus.PUBLISHED)
    }

    /**
     * 반려
     */
    fun reject(): Strategy {
        require(status == StrategyStatus.PENDING_REVIEW) { "Only pending strategies can be rejected" }
        return transitionTo(StrategyStatus.REJECTED)
    }

    /**
     * 보관
     */
    fun archive(): Strategy = transitionTo(StrategyStatus.ARCHIVED)

    /**
     * 구독자 수 증가
     */
    fun incrementSubscriberCount(): Strategy {
        return copy(subscriberCount = subscriberCount + 1, updatedAt = LocalDateTime.now())
    }

    /**
     * 구독자 수 감소
     */
    fun decrementSubscriberCount(): Strategy {
        require(subscriberCount > 0) { "Subscriber count cannot be negative" }
        return copy(subscriberCount = subscriberCount - 1, updatedAt = LocalDateTime.now())
    }

    /**
     * 평점 업데이트
     */
    fun updateRating(newRating: BigDecimal): Strategy {
        require(newRating >= BigDecimal.ZERO && newRating <= BigDecimal.valueOf(5)) {
            "Rating must be between 0 and 5"
        }
        return copy(averageRating = newRating, updatedAt = LocalDateTime.now())
    }

    /**
     * 발행 가능 여부
     */
    fun canPublish(): Boolean = status == StrategyStatus.APPROVED

    /**
     * 마켓플레이스에 노출 가능 여부
     */
    fun isVisibleInMarketplace(): Boolean = status == StrategyStatus.PUBLISHED && isPublic
}
