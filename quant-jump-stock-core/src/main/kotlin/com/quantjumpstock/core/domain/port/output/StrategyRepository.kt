package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.strategy.Strategy
import com.quantjumpstock.core.domain.model.strategy.StrategyStatus

/**
 * Strategy Repository Port - 도메인 포트
 *
 * 특징:
 * - 도메인 모델(Strategy)만 사용
 * - JPA Entity 타입 없음
 * - 구현은 adapter/output/persistence/jpa/adapter/에서 수행
 */
interface StrategyRepository {

    /**
     * 전략 저장 (생성 또는 업데이트)
     */
    fun save(strategy: Strategy): Strategy

    /**
     * ID로 전략 조회
     */
    fun findById(id: Long): Strategy?

    /**
     * 소유자 ID로 전략 목록 조회
     */
    fun findByOwnerId(ownerId: Long): List<Strategy>

    /**
     * 상태로 전략 목록 조회
     */
    fun findByStatus(status: StrategyStatus): List<Strategy>

    /**
     * 카테고리 ID로 전략 목록 조회
     */
    fun findByCategoryId(categoryId: Long): List<Strategy>

    /**
     * 마켓플레이스에 노출될 전략 목록 조회 (공개 + 발행됨)
     */
    fun findPublishedAndPublic(): List<Strategy>

    /**
     * 전체 전략 조회
     */
    fun findAll(): List<Strategy>

    /**
     * 전략 삭제
     */
    fun deleteById(id: Long)

    /**
     * 전략 존재 여부 확인
     */
    fun existsById(id: Long): Boolean

    /**
     * ID로 전략 조회 (백테스트 결과 포함)
     */
    fun findByIdWithBacktestResults(id: Long): Strategy?
}
