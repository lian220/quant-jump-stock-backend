package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface BacktestResultJpaRepository : JpaRepository<BacktestResultEntity, Long> {

    fun findByStrategyId(strategyId: Long): List<BacktestResultEntity>

    fun findByUserId(userId: Long): List<BacktestResultEntity>

    fun findByStatus(status: BacktestStatus): List<BacktestResultEntity>

    // 페이징 조회
    fun findByStrategyIdOrderByCreatedAtDesc(strategyId: Long, pageable: Pageable): Page<BacktestResultEntity>

    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<BacktestResultEntity>

    // 전략 + 유저 필터링 (유저 격리)
    fun findByStrategyIdAndUserIdOrderByCreatedAtDesc(strategyId: Long, userId: Long, pageable: Pageable): Page<BacktestResultEntity>

    // 전략의 최신 완료된 백테스트
    @Query("""
        SELECT br FROM BacktestResultEntity br
        WHERE br.strategy.id = :strategyId AND br.status = 'COMPLETED'
        ORDER BY br.createdAt DESC
    """)
    fun findLatestCompletedByStrategyId(strategyId: Long, pageable: Pageable): List<BacktestResultEntity>

    // 거래 내역 포함 조회
    @Query("""
        SELECT br FROM BacktestResultEntity br
        LEFT JOIN FETCH br.trades
        WHERE br.id = :id
    """)
    fun findByIdWithTrades(id: Long): Optional<BacktestResultEntity>

    fun findByRequestId(requestId: String): BacktestResultEntity?

    // 기간별 조회
    @Query("""
        SELECT br FROM BacktestResultEntity br
        WHERE br.strategy.id = :strategyId
        AND br.startDate = :startDate
        AND br.endDate = :endDate
        AND br.status = 'COMPLETED'
        ORDER BY br.createdAt DESC
    """)
    fun findByStrategyIdAndDateRange(strategyId: Long, startDate: LocalDate, endDate: LocalDate): List<BacktestResultEntity>

    // 사용자의 최근 백테스트
    @Query("""
        SELECT br FROM BacktestResultEntity br
        JOIN FETCH br.strategy s
        WHERE br.user.id = :userId
        ORDER BY br.createdAt DESC
    """)
    fun findRecentByUserIdWithStrategy(userId: Long, pageable: Pageable): Page<BacktestResultEntity>

    // 오늘 실행한 백테스트 수 (티어 제한용)
    @Query("""
        SELECT COUNT(br) FROM BacktestResultEntity br
        WHERE br.user.id = :userId
        AND br.createdAt >= :since
    """)
    fun countByUserIdSince(userId: Long, since: LocalDateTime): Long

    // 통계
    @Query("SELECT COUNT(br) FROM BacktestResultEntity br WHERE br.user.id = :userId AND br.status = 'COMPLETED'")
    fun countCompletedByUserId(userId: Long): Long

    @Query("SELECT COUNT(br) FROM BacktestResultEntity br WHERE br.strategy.id = :strategyId AND br.status = 'COMPLETED'")
    fun countCompletedByStrategyId(strategyId: Long): Long

    // 성과 지표 평균 (전략 평가용)
    @Query("""
        SELECT AVG(br.cagr), AVG(br.mdd), AVG(br.sharpeRatio), AVG(br.winRate)
        FROM BacktestResultEntity br
        WHERE br.strategy.id = :strategyId AND br.status = 'COMPLETED'
    """)
    fun getAverageMetricsByStrategyId(strategyId: Long): List<Array<Any?>>

    // 베스트 퍼포먼스
    @Query("""
        SELECT br FROM BacktestResultEntity br
        WHERE br.strategy.id = :strategyId AND br.status = 'COMPLETED'
        ORDER BY br.cagr DESC
    """)
    fun findBestPerformingByStrategyId(strategyId: Long, pageable: Pageable): List<BacktestResultEntity>

    // SCRUM-344: Canonical 백테스트 조회
    @Query("""
        SELECT br FROM BacktestResultEntity br
        WHERE br.strategy.id = :strategyId
        AND br.backtestType = 'CANONICAL'
        AND br.status = 'COMPLETED'
        ORDER BY br.createdAt DESC
    """)
    fun findCanonicalByStrategyId(strategyId: Long, pageable: Pageable): List<BacktestResultEntity>

    // SCRUM-344: 사용자 커스텀 백테스트 수 조회
    @Query("""
        SELECT COUNT(br) FROM BacktestResultEntity br
        WHERE br.user.id = :userId
        AND br.strategy.id = :strategyId
        AND br.backtestType = 'USER_CUSTOM'
    """)
    fun countUserCustomByUserIdAndStrategyId(userId: Long, strategyId: Long): Long

    // SCRUM-344: 전략별 백테스트 타입 필터 조회
    fun findByStrategyIdAndBacktestTypeOrderByCreatedAtDesc(
        strategyId: Long, backtestType: String, pageable: Pageable
    ): Page<BacktestResultEntity>

    // 중복 실행 방지: 동일 설정의 USER_CUSTOM 백테스트 조회
    @Query("""
        SELECT br FROM BacktestResultEntity br
        WHERE br.user.id = :userId
        AND br.strategy.id = :strategyId
        AND br.benchmark = :benchmark
        AND br.initialCapital = :initialCapital
        AND br.backtestType = 'USER_CUSTOM'
        AND br.status IN ('COMPLETED', 'RUNNING')
        ORDER BY br.createdAt DESC
    """)
    fun findUserCustomBySettings(
        userId: Long, strategyId: Long,
        benchmark: String, initialCapital: BigDecimal
    ): List<BacktestResultEntity>
}
