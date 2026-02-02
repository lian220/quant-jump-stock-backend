package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface StrategySignalJpaRepository : JpaRepository<StrategySignalEntity, Long> {

    fun findByStrategyId(strategyId: Long): List<StrategySignalEntity>

    fun findByStrategyIdOrderBySignalDateDesc(strategyId: Long, pageable: Pageable): Page<StrategySignalEntity>

    fun findByStrategyIdAndSignalType(strategyId: Long, signalType: SignalType): List<StrategySignalEntity>

    // 최근 신호
    @Query("""
        SELECT ss FROM StrategySignalEntity ss
        WHERE ss.strategy.id = :strategyId
        AND ss.signalDate >= :since
        ORDER BY ss.signalDate DESC
    """)
    fun findRecentSignals(strategyId: Long, since: LocalDate): List<StrategySignalEntity>

    // 특정 날짜 신호
    fun findByStrategyIdAndSignalDate(strategyId: Long, signalDate: LocalDate): List<StrategySignalEntity>

    // 특정 종목 신호
    @Query("""
        SELECT ss FROM StrategySignalEntity ss
        WHERE ss.strategy.id = :strategyId
        AND ss.ticker = :ticker
        ORDER BY ss.signalDate DESC
    """)
    fun findByStrategyIdAndTicker(strategyId: Long, ticker: String, pageable: Pageable): List<StrategySignalEntity>

    // 여러 전략의 최근 신호 (구독한 전략들)
    @Query("""
        SELECT ss FROM StrategySignalEntity ss
        WHERE ss.strategy.id IN :strategyIds
        AND ss.signalDate >= :since
        ORDER BY ss.signalDate DESC
    """)
    fun findRecentSignalsForStrategies(strategyIds: List<Long>, since: LocalDate): List<StrategySignalEntity>

    // 오늘의 신호
    @Query("""
        SELECT ss FROM StrategySignalEntity ss
        WHERE ss.strategy.id = :strategyId
        AND ss.signalDate = CURRENT_DATE
        ORDER BY ss.createdAt DESC
    """)
    fun findTodaySignals(strategyId: Long): List<StrategySignalEntity>

    // BUY 신호의 종목들
    @Query("""
        SELECT DISTINCT ss.ticker FROM StrategySignalEntity ss
        WHERE ss.strategy.id = :strategyId
        AND ss.signalDate >= :since
        AND ss.signalType = 'BUY'
    """)
    fun findRecentBuyTickers(strategyId: Long, since: LocalDate): List<String>

    // 신호 타입별 통계
    @Query("""
        SELECT ss.signalType, COUNT(ss)
        FROM StrategySignalEntity ss
        WHERE ss.strategy.id = :strategyId
        AND ss.signalDate >= :since
        GROUP BY ss.signalType
    """)
    fun getSignalTypeDistribution(strategyId: Long, since: LocalDate): List<Array<Any>>

    // 통계
    @Query("SELECT COUNT(ss) FROM StrategySignalEntity ss WHERE ss.strategy.id = :strategyId AND ss.signalDate >= :since")
    fun countRecentSignals(strategyId: Long, since: LocalDate): Long
}
