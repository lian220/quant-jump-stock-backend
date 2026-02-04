package com.quantjumpstock.core.domain.model.strategy

/**
 * 전략 상태 - 순수 도메인 Enum
 *
 * adapter/output/persistence/jpa/StrategyEntity.kt의 StrategyStatus를
 * 도메인 계층으로 이동
 */
enum class StrategyStatus {
    DRAFT,           // 초안 - 사용자가 생성 후 아직 제출하지 않음
    PENDING_REVIEW,  // 검토 대기 - 관리자 승인 대기 중
    APPROVED,        // 승인됨 - 관리자가 승인, 발행 가능
    PUBLISHED,       // 발행됨 - 마켓플레이스에 공개
    REJECTED,        // 반려됨 - 관리자가 반려
    ACTIVE,          // 활성 (레거시 호환)
    ARCHIVED;        // 보관됨 - 비활성화

    fun canTransitionTo(newStatus: StrategyStatus): Boolean = when (this) {
        DRAFT -> newStatus in listOf(PENDING_REVIEW, ARCHIVED)
        PENDING_REVIEW -> newStatus in listOf(APPROVED, REJECTED, DRAFT)
        APPROVED -> newStatus in listOf(PUBLISHED, ARCHIVED, DRAFT)
        PUBLISHED -> newStatus in listOf(ARCHIVED, APPROVED)
        REJECTED -> newStatus in listOf(DRAFT, ARCHIVED)
        ACTIVE -> newStatus in listOf(ARCHIVED, PUBLISHED)
        ARCHIVED -> newStatus == DRAFT
    }
}
