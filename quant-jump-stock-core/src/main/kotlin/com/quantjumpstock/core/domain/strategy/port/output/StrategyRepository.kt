package com.quantjumpstock.core.domain.strategy.port.output

import com.quantjumpstock.core.adapter.output.persistence.jpa.StrategyEntity
import java.util.Optional

/**
 * 전략 저장소 인터페이스 (Output Port)
 * 전략 영속성 계층에 접근하는 인터페이스입니다.
 */
interface StrategyRepository {

    /**
     * 전략 저장
     */
    fun save(strategy: StrategyEntity): StrategyEntity

    /**
     * ID로 전략 조회
     */
    fun findById(id: Long): Optional<StrategyEntity>

    /**
     * ID로 전략 조회 (백테스트 결과 포함)
     */
    fun findByIdWithBacktestResults(id: Long): Optional<StrategyEntity>

    /**
     * 소유자 ID로 전략 목록 조회
     */
    fun findByOwnerId(ownerId: Long): List<StrategyEntity>

    /**
     * 전략 삭제
     */
    fun delete(strategy: StrategyEntity)

    /**
     * 전략 존재 여부 확인
     */
    fun existsById(id: Long): Boolean
}
