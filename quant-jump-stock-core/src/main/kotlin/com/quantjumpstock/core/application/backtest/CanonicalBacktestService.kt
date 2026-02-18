package com.quantjumpstock.core.application.backtest

import com.quantjumpstock.core.domain.model.backtest.BacktestResult
import com.quantjumpstock.core.domain.model.backtest.BacktestStatus
import com.quantjumpstock.core.domain.model.backtest.BacktestType
import com.quantjumpstock.core.domain.model.backtest.UniverseType
import com.quantjumpstock.core.domain.model.*
import com.quantjumpstock.core.domain.economic.port.output.MessagePublisher
import com.quantjumpstock.core.domain.model.strategy.StrategyStatus
import com.quantjumpstock.core.domain.port.output.BacktestResultRepository
import com.quantjumpstock.core.domain.port.output.Benchmark
import com.quantjumpstock.core.domain.port.output.StockRepository
import com.quantjumpstock.core.domain.port.output.StrategyRepository
import com.quantjumpstock.core.events.EventTopics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * SCRUM-344: Canonical Backtest Service
 *
 * 전략별 대표(Canonical) 백테스트를 관리합니다.
 * - 표준 파라미터(1년, 1천만원, SPY)로 자동 실행
 * - PUBLISHED 전략 대상 일괄 갱신
 * - 완료 시 strategies.canonical_backtest_id 업데이트
 */
@Service
class CanonicalBacktestService(
    @Qualifier("strategyPersistenceAdapterV2")
    private val strategyRepository: StrategyRepository,
    private val backtestResultRepository: BacktestResultRepository,
    private val stockRepository: StockRepository,
    private val messagePublisher: MessagePublisher
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        val CANONICAL_INITIAL_CAPITAL: BigDecimal = BigDecimal("10000000")
        const val CANONICAL_BENCHMARK = "^GSPC"
        const val CANONICAL_PERIOD_DAYS = 365L
    }

    /**
     * 특정 전략의 Canonical 백테스트 실행
     */
    @Transactional
    fun runCanonicalBacktest(strategyId: Long) {
        val strategy = strategyRepository.findById(strategyId)
            ?: run {
                logger.warn("Canonical 백테스트 대상 전략 없음: strategyId={}", strategyId)
                return
            }

        val requestId = UUID.randomUUID().toString()
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(CANONICAL_PERIOD_DAYS)
        val timestamp = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toString()

        // 전략의 추천 유니버스 타입에 따라 종목 결정
        val tickers = stockRepository.findAll().mapNotNull { it.ticker }
        if (tickers.isEmpty()) {
            logger.warn("Canonical 백테스트 대상 종목 없음: strategyId={}", strategyId)
            return
        }

        logger.info("Canonical 백테스트 실행: strategyId={}, requestId={}, tickers={}", strategyId, requestId, tickers.size)

        // RUNNING placeholder 생성
        val placeholder = BacktestResult(
            requestId = requestId,
            strategyId = strategyId,
            startDate = startDate,
            endDate = endDate,
            initialCapital = CANONICAL_INITIAL_CAPITAL,
            benchmark = CANONICAL_BENCHMARK,
            benchmarks = listOf(CANONICAL_BENCHMARK),
            finalValue = BigDecimal.ZERO,
            totalReturn = BigDecimal.ZERO,
            cagr = BigDecimal.ZERO,
            mdd = BigDecimal.ZERO,
            universeType = UniverseType.MARKET,
            backtestType = BacktestType.CANONICAL,
            status = BacktestStatus.RUNNING
        )
        val savedPlaceholder = backtestResultRepository.save(placeholder)

        // Pub/Sub으로 백테스트 요청 발행
        val backtestRequest = BacktestRequest(
            requestId = requestId,
            strategyId = strategyId,
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            initialCapital = CANONICAL_INITIAL_CAPITAL,
            timestamp = timestamp,
            source = "canonical-backtest",
            tickers = tickers,
            benchmark = CANONICAL_BENCHMARK,
            benchmarks = listOf(CANONICAL_BENCHMARK),
            rebalancePeriod = "MONTHLY",
            universeType = UniverseType.MARKET.name,
            backtestType = BacktestType.CANONICAL.name
        )

        try {
            messagePublisher.publishBacktestRequest(
                topic = EventTopics.BACKTEST_REQUEST,
                request = backtestRequest
            )
            logger.info("Canonical 백테스트 요청 발행: strategyId={}, requestId={}", strategyId, requestId)
        } catch (e: Exception) {
            logger.error("Canonical 백테스트 발행 실패, RUNNING → FAILED 처리: strategyId={}, requestId={}", strategyId, requestId, e)
            savedPlaceholder.id?.let { id ->
                backtestResultRepository.save(
                    savedPlaceholder.copy(
                        status = BacktestStatus.FAILED,
                        errorMessage = "Pub/Sub 발행 실패: ${e.message}",
                        completedAt = java.time.LocalDateTime.now()
                    )
                )
            }
            throw e
        }
    }

    /**
     * 모든 PUBLISHED 전략의 Canonical 백테스트 갱신
     */
    fun refreshAllCanonicalBacktests() {
        val publishedStrategies = strategyRepository.findByStatus(StrategyStatus.PUBLISHED)
        logger.info("Canonical 백테스트 일괄 갱신 시작: {} 개 전략", publishedStrategies.size)

        publishedStrategies.forEach { strategy ->
            val strategyId = strategy.id ?: run {
                logger.warn("ID가 없는 전략은 Canonical 백테스트를 실행할 수 없습니다: strategyName={}", strategy.name)
                return@forEach
            }
            try {
                runCanonicalBacktest(strategyId)
            } catch (e: Exception) {
                logger.error("Canonical 백테스트 실행 실패: strategyId={}", strategyId, e)
            }
        }

        logger.info("Canonical 백테스트 일괄 갱신 완료")
    }

    /**
     * Canonical 백테스트 완료 시 전략의 canonical_backtest_id 업데이트
     */
    @Transactional
    fun onCanonicalBacktestCompleted(strategyId: Long, backtestResultId: Long) {
        val strategy = strategyRepository.findById(strategyId) ?: return
        val updated = strategy.copy(canonicalBacktestId = backtestResultId)
        strategyRepository.save(updated)
        logger.info("전략 canonical_backtest_id 업데이트: strategyId={}, backtestResultId={}", strategyId, backtestResultId)
    }
}
