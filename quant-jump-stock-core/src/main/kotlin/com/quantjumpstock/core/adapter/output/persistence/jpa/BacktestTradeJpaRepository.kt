package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface BacktestTradeJpaRepository : JpaRepository<BacktestTradeEntity, Long> {

    fun findByBacktestResultId(backtestId: Long): List<BacktestTradeEntity>

    fun findByBacktestResultIdOrderByTradeDateAsc(backtestId: Long): List<BacktestTradeEntity>

    fun findByBacktestResultIdOrderByTradeDateAsc(backtestId: Long, pageable: Pageable): Page<BacktestTradeEntity>

    fun findByBacktestResultIdAndTicker(backtestId: Long, ticker: String): List<BacktestTradeEntity>

    fun findByBacktestResultIdAndSide(backtestId: Long, side: BacktestTradeSide): List<BacktestTradeEntity>

    // 기간별 거래 조회
    @Query("""
        SELECT bt FROM BacktestTradeEntity bt
        WHERE bt.backtestResult.id = :backtestId
        AND bt.tradeDate BETWEEN :startDate AND :endDate
        ORDER BY bt.tradeDate ASC
    """)
    fun findByBacktestIdAndDateRange(backtestId: Long, startDate: LocalDate, endDate: LocalDate): List<BacktestTradeEntity>

    // 종목별 거래 통계
    @Query("""
        SELECT bt.ticker, COUNT(bt), SUM(bt.pnl)
        FROM BacktestTradeEntity bt
        WHERE bt.backtestResult.id = :backtestId AND bt.pnl IS NOT NULL
        GROUP BY bt.ticker
        ORDER BY SUM(bt.pnl) DESC
    """)
    fun getTradeStatsByTicker(backtestId: Long): List<Array<Any?>>

    // 수익/손실 거래 분리
    @Query("""
        SELECT bt FROM BacktestTradeEntity bt
        WHERE bt.backtestResult.id = :backtestId AND bt.pnl IS NOT NULL AND bt.pnl > 0
        ORDER BY bt.pnl DESC
    """)
    fun findWinningTrades(backtestId: Long, pageable: Pageable): List<BacktestTradeEntity>

    @Query("""
        SELECT bt FROM BacktestTradeEntity bt
        WHERE bt.backtestResult.id = :backtestId AND bt.pnl IS NOT NULL AND bt.pnl < 0
        ORDER BY bt.pnl ASC
    """)
    fun findLosingTrades(backtestId: Long, pageable: Pageable): List<BacktestTradeEntity>

    // 통계
    @Query("SELECT COUNT(bt) FROM BacktestTradeEntity bt WHERE bt.backtestResult.id = :backtestId")
    fun countByBacktestId(backtestId: Long): Long

    @Query("SELECT COUNT(bt) FROM BacktestTradeEntity bt WHERE bt.backtestResult.id = :backtestId AND bt.pnl > 0")
    fun countWinningTrades(backtestId: Long): Long

    @Query("SELECT COUNT(bt) FROM BacktestTradeEntity bt WHERE bt.backtestResult.id = :backtestId AND bt.pnl < 0")
    fun countLosingTrades(backtestId: Long): Long
}
