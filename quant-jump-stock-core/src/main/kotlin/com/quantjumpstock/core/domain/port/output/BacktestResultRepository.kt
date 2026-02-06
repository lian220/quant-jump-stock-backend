package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.backtest.BacktestResult
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * BacktestResult Repository Port - 도메인 포트
 *
 * 특징:
 * - 도메인 모델(BacktestResult)만 사용
 * - JPA Entity 타입 없음
 * - 구현은 adapter/output/persistence/에서 수행
 */
interface BacktestResultRepository {

    /**
     * 백테스트 결과 저장 (생성 또는 업데이트)
     */
    fun save(result: BacktestResult): BacktestResult

    /**
     * ID로 백테스트 결과 조회
     */
    fun findById(id: Long): BacktestResult?

    /**
     * ID로 백테스트 결과 조회 (거래 내역 포함)
     */
    fun findByIdWithTrades(id: Long): BacktestResult?

    /**
     * 전략 ID로 백테스트 결과 목록 조회 (최신순)
     */
    fun findByStrategyIdOrderByCreatedAtDesc(strategyId: Long, pageable: Pageable): Page<BacktestResult>

    /**
     * 사용자 ID로 백테스트 결과 목록 조회 (최신순)
     */
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<BacktestResult>

    /**
     * 전체 백테스트 결과 페이징 조회
     */
    fun findAll(pageable: Pageable): Page<BacktestResult>
}
