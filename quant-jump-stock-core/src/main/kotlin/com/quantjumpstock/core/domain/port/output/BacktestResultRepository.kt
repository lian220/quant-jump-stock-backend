package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.backtest.BacktestResult
import com.quantjumpstock.core.domain.model.backtest.BacktestType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.math.BigDecimal

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
     * requestId로 백테스트 결과 조회
     */
    fun findByRequestId(requestId: String): BacktestResult?

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
     * 전략 ID + 사용자 ID로 백테스트 결과 목록 조회 (유저 격리)
     */
    fun findByStrategyIdAndUserIdOrderByCreatedAtDesc(strategyId: Long, userId: Long, pageable: Pageable): Page<BacktestResult>

    /**
     * 전체 백테스트 결과 페이징 조회
     */
    fun findAll(pageable: Pageable): Page<BacktestResult>

    /**
     * SCRUM-344: 전략의 Canonical 백테스트 조회
     */
    fun findCanonicalByStrategyId(strategyId: Long): BacktestResult?

    /**
     * SCRUM-344: 사용자의 전략별 커스텀 백테스트 수 조회
     */
    fun countUserCustomByUserIdAndStrategyId(userId: Long, strategyId: Long): Long

    /**
     * SCRUM-344: 전략의 COMPLETED 백테스트 목록 조회 (특정 타입)
     */
    fun findByStrategyIdAndBacktestType(strategyId: Long, backtestType: BacktestType, pageable: Pageable): Page<BacktestResult>

    /**
     * ID로 백테스트 결과 삭제
     */
    fun deleteById(id: Long)

    /**
     * 중복 실행 방지: 동일 설정(벤치마크+자본금)의 USER_CUSTOM 백테스트 조회
     */
    fun findUserCustomBySettings(
        userId: Long, strategyId: Long,
        benchmark: String, initialCapital: BigDecimal
    ): List<BacktestResult>
}
