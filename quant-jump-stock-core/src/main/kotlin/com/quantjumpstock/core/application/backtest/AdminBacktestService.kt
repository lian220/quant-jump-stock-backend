package com.quantjumpstock.core.application.backtest

import com.quantjumpstock.core.domain.model.BacktestRequest
import com.quantjumpstock.core.domain.model.backtest.BacktestResult
import com.quantjumpstock.core.domain.model.backtest.BacktestStatus
import com.quantjumpstock.core.domain.model.backtest.BacktestType
import com.quantjumpstock.core.domain.model.backtest.UniverseType
import com.quantjumpstock.core.domain.model.strategy.StrategyStatus
import com.quantjumpstock.core.domain.economic.port.output.MessagePublisher
import com.quantjumpstock.core.domain.port.output.BacktestResultRepository
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
 * Admin 백테스트 일괄 실행 서비스
 *
 * 어드민에서 커스텀 기간으로 전체/특정 전략의 백테스트를 즉시 실행합니다.
 * CanonicalBacktestService와 유사하지만 기간 파라미터를 외부에서 받습니다.
 */
@Service
class AdminBacktestService(
    @Qualifier("strategyPersistenceAdapterV2")
    private val strategyRepository: StrategyRepository,
    private val backtestResultRepository: BacktestResultRepository,
    private val stockRepository: StockRepository,
    private val messagePublisher: MessagePublisher
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        val DEFAULT_INITIAL_CAPITAL: BigDecimal = BigDecimal("10000000")
        const val DEFAULT_BENCHMARK = "^GSPC"
        const val DEFAULT_PERIOD_DAYS = 365L
        const val MAX_PERIOD_DAYS = 1460L
    }

    /**
     * 특정 전략의 백테스트를 커스텀 기간으로 실행
     *
     * @return 실행 정보 (requestId, startDate, endDate)
     */
    @Transactional
    fun runBacktest(
        strategyId: Long,
        periodDays: Long = DEFAULT_PERIOD_DAYS,
        benchmark: String = DEFAULT_BENCHMARK,
        initialCapital: BigDecimal = DEFAULT_INITIAL_CAPITAL
    ): AdminBacktestRunResult {
        val effectivePeriod = periodDays.coerceIn(1, MAX_PERIOD_DAYS)

        val strategy = strategyRepository.findById(strategyId)
            ?: throw IllegalArgumentException("전략을 찾을 수 없습니다: strategyId=$strategyId")

        val requestId = UUID.randomUUID().toString()
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(effectivePeriod)
        val timestamp = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toString()

        val tickers = stockRepository.findAll().mapNotNull { it.ticker }
        if (tickers.isEmpty()) {
            throw IllegalStateException("백테스트 대상 종목이 없습니다. stocks 테이블에 종목을 등록해주세요.")
        }

        logger.info(
            "Admin 백테스트 실행: strategyId={}, requestId={}, period={}일, tickers={}",
            strategyId, requestId, effectivePeriod, tickers.size
        )

        // RUNNING placeholder 생성
        val placeholder = BacktestResult(
            requestId = requestId,
            strategyId = strategyId,
            startDate = startDate,
            endDate = endDate,
            initialCapital = initialCapital,
            benchmark = benchmark,
            benchmarks = listOf(benchmark),
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
            initialCapital = initialCapital,
            timestamp = timestamp,
            source = "admin-backtest",
            tickers = tickers,
            benchmark = benchmark,
            benchmarks = listOf(benchmark),
            rebalancePeriod = "MONTHLY",
            universeType = UniverseType.MARKET.name,
            backtestType = BacktestType.CANONICAL.name,
            forceFull = true
        )

        try {
            messagePublisher.publishBacktestRequest(
                topic = EventTopics.BACKTEST_REQUEST,
                request = backtestRequest
            )
            logger.info("Admin 백테스트 요청 발행 완료: strategyId={}, requestId={}", strategyId, requestId)
        } catch (e: Exception) {
            logger.error("Admin 백테스트 발행 실패: strategyId={}, requestId={}", strategyId, requestId, e)
            savedPlaceholder.id?.let {
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

        return AdminBacktestRunResult(
            strategyId = strategyId,
            requestId = requestId,
            startDate = startDate.toString(),
            endDate = endDate.toString()
        )
    }

    /**
     * PUBLISHED 전략 전체의 백테스트를 커스텀 기간으로 실행
     *
     * @return 트리거된 전략 수와 ID 목록
     */
    fun runAllBacktests(
        periodDays: Long = DEFAULT_PERIOD_DAYS,
        benchmark: String = DEFAULT_BENCHMARK,
        initialCapital: BigDecimal = DEFAULT_INITIAL_CAPITAL
    ): AdminBacktestRunAllResult {
        val publishedStrategies = strategyRepository.findByStatus(StrategyStatus.PUBLISHED) +
            strategyRepository.findByStatus(StrategyStatus.ACTIVE)
        logger.info("Admin 백테스트 일괄 실행 시작: {}개 전략, period={}일", publishedStrategies.size, periodDays)

        val triggeredIds = mutableListOf<Long>()

        publishedStrategies.forEach { strategy ->
            val strategyId = strategy.id ?: run {
                logger.warn("ID가 없는 전략은 백테스트를 실행할 수 없습니다: strategyName={}", strategy.name)
                return@forEach
            }
            try {
                runBacktest(strategyId, periodDays, benchmark, initialCapital)
                triggeredIds.add(strategyId)
            } catch (e: Exception) {
                logger.error("Admin 백테스트 실행 실패: strategyId={}", strategyId, e)
            }
        }

        logger.info("Admin 백테스트 일괄 실행 완료: {}/{}개 성공", triggeredIds.size, publishedStrategies.size)

        return AdminBacktestRunAllResult(
            triggered = triggeredIds.size,
            strategyIds = triggeredIds
        )
    }
}

data class AdminBacktestRunResult(
    val strategyId: Long,
    val requestId: String,
    val startDate: String,
    val endDate: String
)

data class AdminBacktestRunAllResult(
    val triggered: Int,
    val strategyIds: List<Long>
)
