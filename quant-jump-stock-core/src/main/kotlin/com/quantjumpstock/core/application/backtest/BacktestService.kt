package com.quantjumpstock.core.application.backtest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.quantjumpstock.core.domain.model.backtest.BacktestGradeCalculator
import com.quantjumpstock.core.domain.model.backtest.BacktestResult
import com.quantjumpstock.core.domain.model.backtest.BacktestTrade
import com.quantjumpstock.core.domain.economic.port.output.MessagePublisher
import com.quantjumpstock.core.domain.model.*
import com.quantjumpstock.core.domain.port.output.BacktestResultRepository
import com.quantjumpstock.core.events.EventTopics
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * 백테스트 Application Service
 * 백테스트 요청을 처리하고 Kafka로 이벤트를 발행합니다.
 */
@Service
class BacktestService(
    private val messagePublisher: MessagePublisher,
    private val backtestResultRepository: BacktestResultRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 백테스트 실행 요청
     * POST /api/v1/backtest/run
     */
    fun runBacktest(request: BacktestRunRequest, userId: String?): BacktestRunResponse {
        val requestId = UUID.randomUUID().toString()
        val timestamp = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toString()

        logger.info("백테스트 실행 요청: requestId=$requestId, strategyId=${request.strategyId}")

        val backtestRequest = BacktestRequest(
            requestId = requestId,
            strategyId = request.strategyId,
            startDate = request.startDate,
            endDate = request.endDate,
            initialCapital = request.initialCapital,
            timestamp = timestamp,
            source = "quantiq-core",
            userId = userId,
            tickers = request.tickers,
            benchmark = request.benchmark,
            rebalancePeriod = request.rebalancePeriod.name,
            // SCRUM-258: 리스크 파라미터 변환
            riskSettings = request.riskSettings?.let { mapRiskSettings(it) },
            positionSizing = request.positionSizing?.let { mapPositionSizing(it) },
            tradingCosts = request.tradingCosts?.let { mapTradingCosts(it) }
        )

        try {
            messagePublisher.publishBacktestRequest(
                topic = EventTopics.BACKTEST_REQUEST,
                request = backtestRequest
            )

            logger.info("백테스트 요청 발행 성공: requestId=$requestId")

            // 예상 소요 시간 계산 (간단한 휴리스틱)
            val estimatedTime = calculateEstimatedTime(request)

            return BacktestRunResponse(
                backtestId = requestId,
                status = "PENDING",
                estimatedTime = estimatedTime,
                message = "백테스트가 시작되었습니다."
            )
        } catch (e: Exception) {
            logger.error("백테스트 요청 실패: requestId=$requestId", e)
            throw BacktestException("백테스트 요청에 실패했습니다: ${e.message}", e)
        }
    }

    /**
     * 백테스트 결과 조회
     * GET /api/v1/backtest/{id}
     */
    fun getBacktestResult(id: Long): BacktestResultResponse {
        val result = backtestResultRepository.findByIdWithTrades(id)
            ?: throw BacktestNotFoundException("백테스트 결과를 찾을 수 없습니다: id=$id")

        return mapToResultResponse(result)
    }

    /**
     * 백테스트 목록 조회
     * GET /api/v1/backtest
     */
    fun getBacktestList(
        strategyId: Long?,
        userId: Long?,
        page: Int,
        size: Int
    ): PagedResponse<BacktestListItemResponse> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))

        val pageResult = when {
            strategyId != null -> backtestResultRepository.findByStrategyIdOrderByCreatedAtDesc(strategyId, pageable)
            userId != null -> backtestResultRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            else -> backtestResultRepository.findAll(pageable)
        }

        val content = pageResult.content.map { mapToListItemResponse(it) }

        return PagedResponse(
            content = content,
            page = safePage,
            size = safeSize,
            totalElements = pageResult.totalElements,
            totalPages = pageResult.totalPages
        )
    }

    /**
     * ID 해석: DB ID(Long) 또는 requestId(UUID String) → DB ID(Long)
     */
    fun resolveBacktestId(idOrRequestId: String): Long {
        // Long으로 파싱 가능하면 DB ID
        idOrRequestId.toLongOrNull()?.let { return it }

        // UUID requestId로 조회
        val result = backtestResultRepository.findByRequestId(idOrRequestId)
            ?: throw BacktestNotFoundException("백테스트 결과를 찾을 수 없습니다: requestId=$idOrRequestId")
        return requireNotNull(result.id) { "BacktestResult.id must not be null" }
    }

    /**
     * 초보자용 Enhanced 백테스트 결과 조회
     * GET /api/v1/backtest/{id}/enhanced
     * SCRUM-245: 신호등 시스템 + 평문 설명 + 용어 사전
     */
    fun getEnhancedBacktestResult(id: Long): BacktestEnhancedResponse {
        val result = backtestResultRepository.findByIdWithTrades(id)
            ?: throw BacktestNotFoundException("백테스트 결과를 찾을 수 없습니다: id=$id")

        val equityCurve = result.equityCurve?.let { parseEquityCurve(it) }

        // 등급 계산
        val gradedMetrics = listOf(
            BacktestGradeCalculator.gradeCagr(result.cagr),
            BacktestGradeCalculator.gradeMdd(result.mdd),
            BacktestGradeCalculator.gradeSharpe(result.sharpeRatio),
            BacktestGradeCalculator.gradeWinRate(result.winRate),
            BacktestGradeCalculator.gradeSortino(result.sortinoRatio),
            BacktestGradeCalculator.gradeVolatility(result.volatility)
        )

        val overallGrade = BacktestGradeCalculator.calculateOverallGrade(
            cagr = result.cagr,
            mdd = result.mdd,
            sharpe = result.sharpeRatio,
            winRate = result.winRate
        )

        // 평문 요약
        val summary = BacktestGradeCalculator.generatePlainSummary(result)

        return BacktestEnhancedResponse(
            id = requireNotNull(result.id),
            strategyId = result.strategyId,
            strategyName = result.strategyName,
            status = result.status.name,
            startDate = result.startDate,
            endDate = result.endDate,
            initialCapital = result.initialCapital,
            finalValue = result.finalValue,
            overallGrade = MetricGradeResponse.from(overallGrade),
            summary = summary,
            gradedMetrics = gradedMetrics.map { GradedMetricResponse.from(it) },
            equityCurve = equityCurve,
            trades = result.trades.map { mapToTradeResponse(it) },
            glossary = BacktestGlossary.TERMS,
            createdAt = result.createdAt,
            completedAt = result.completedAt
        )
    }

    /**
     * 백테스트 상태 확인
     */
    fun getBacktestStatus(id: Long): String {
        val result = backtestResultRepository.findById(id)
            ?: throw BacktestNotFoundException("백테스트 결과를 찾을 수 없습니다: id=$id")
        return result.status.name
    }

    /**
     * 예상 소요 시간 계산 (초)
     */
    private fun calculateEstimatedTime(request: BacktestRunRequest): Int {
        // 간단한 휴리스틱: 기본 10초 + 종목당 2초 + 기간(년) * 5초
        val baseTime = 10
        val tickerTime = request.tickers.size * 2
        val periodYears = try {
            val start = java.time.LocalDate.parse(request.startDate)
            val end = java.time.LocalDate.parse(request.endDate)
            java.time.temporal.ChronoUnit.YEARS.between(start, end).toInt().coerceAtLeast(1)
        } catch (e: Exception) {
            logger.warn("날짜 파싱 실패, 기본값 사용: startDate=${request.startDate}, endDate=${request.endDate}, error=${e.message}")
            5
        }
        return baseTime + tickerTime + (periodYears * 5)
    }

    /**
     * Domain → ResultResponse 매핑
     */
    private fun mapToResultResponse(result: BacktestResult): BacktestResultResponse {
        val equityCurve = result.equityCurve?.let { parseEquityCurve(it) }

        return BacktestResultResponse(
            id = requireNotNull(result.id) { "BacktestResult.id must not be null when mapping to response" },
            strategyId = result.strategyId,
            strategyName = result.strategyName,
            status = result.status.name,
            metrics = BacktestMetrics(
                cagr = result.cagr,
                mdd = result.mdd,
                sharpeRatio = result.sharpeRatio,
                sortinoRatio = result.sortinoRatio,
                winRate = result.winRate,
                totalReturn = result.totalReturn,
                volatility = result.volatility,
                totalTrades = result.totalTrades,
                winningTrades = result.winningTrades,
                losingTrades = result.losingTrades,
                avgWin = result.avgWin,
                avgLoss = result.avgLoss,
                benchmarkReturn = result.benchmarkReturn,
                alpha = result.alpha,
                beta = result.beta
            ),
            equityCurve = equityCurve,
            benchmarkCurve = equityCurve?.filter { it.benchmark != null }?.map {
                EquityCurvePoint(date = it.date, value = it.benchmark!!)
            }?.ifEmpty { null },
            trades = result.trades.map { mapToTradeResponse(it) },
            createdAt = result.createdAt,
            completedAt = result.completedAt
        )
    }

    /**
     * Domain → ListItemResponse 매핑
     */
    private fun mapToListItemResponse(result: BacktestResult): BacktestListItemResponse {
        return BacktestListItemResponse(
            id = requireNotNull(result.id) { "BacktestResult.id must not be null when mapping to response" },
            strategyId = result.strategyId,
            strategyName = result.strategyName,
            status = result.status.name,
            startDate = result.startDate,
            endDate = result.endDate,
            initialCapital = result.initialCapital,
            finalValue = result.finalValue,
            totalReturn = result.totalReturn,
            cagr = result.cagr,
            mdd = result.mdd,
            sharpeRatio = result.sharpeRatio,
            createdAt = result.createdAt,
            completedAt = result.completedAt
        )
    }

    /**
     * Domain Trade → TradeResponse 매핑
     */
    private fun mapToTradeResponse(trade: BacktestTrade): BacktestTradeResponse {
        return BacktestTradeResponse(
            id = requireNotNull(trade.id) { "BacktestTrade.id must not be null when mapping to response" },
            tradeDate = trade.tradeDate,
            ticker = trade.ticker,
            side = trade.side.name,
            quantity = trade.quantity,
            price = trade.price,
            amount = trade.amount,
            commission = trade.commission,
            pnl = trade.pnl,
            pnlPercent = trade.pnlPercent,
            holdingDays = trade.holdingDays,
            signalReason = trade.signalReason
        )
    }

    /**
     * JSON 문자열 → EquityCurve 파싱
     */
    private fun parseEquityCurve(json: String): List<EquityCurvePoint>? {
        return try {
            objectMapper.readValue<List<Map<String, Any>>>(json).mapNotNull { point ->
                val date = point["date"]?.toString()
                // Data Engine은 "equity" 키로 저장, 하위호환을 위해 "value"도 지원
                val value = (point["equity"] ?: point["value"])?.toString()?.let { BigDecimal(it) }
                val benchmark = point["benchmark"]?.toString()?.let { BigDecimal(it) }
                if (date.isNullOrBlank() || value == null) {
                    null
                } else {
                    EquityCurvePoint(date = date, value = value, benchmark = benchmark)
                }
            }
        } catch (e: Exception) {
            logger.warn("EquityCurve 파싱 실패: ${e.message}")
            null
        }
    }

    // ============================================================================
    // SCRUM-258: 리스크 파라미터 매핑
    // ============================================================================

    private fun mapRiskSettings(settings: RiskSettings): RiskSettingsModel {
        return RiskSettingsModel(
            stopLoss = settings.stopLoss?.let {
                StopSettingsModel(
                    enabled = it.enabled,
                    type = it.type.name.lowercase(),
                    value = it.value
                )
            },
            takeProfit = settings.takeProfit?.let {
                StopSettingsModel(
                    enabled = it.enabled,
                    type = it.type.name.lowercase(),
                    value = it.value
                )
            },
            trailingStop = settings.trailingStop?.let {
                TrailingStopModel(
                    enabled = it.enabled,
                    type = it.type.name.lowercase(),
                    value = it.value,
                    activationThreshold = it.activationThreshold
                )
            }
        )
    }

    private fun mapPositionSizing(sizing: PositionSizing): PositionSizingModel {
        return PositionSizingModel(
            method = sizing.method.name.lowercase(),
            maxPositionPct = sizing.maxPositionPct,
            maxPositions = sizing.maxPositions,
            riskPerTrade = sizing.riskPerTrade
        )
    }

    private fun mapTradingCosts(costs: TradingCosts): TradingCostsModel {
        return TradingCostsModel(
            commission = costs.commission,
            tax = costs.tax,
            slippageModel = costs.slippageModel?.let {
                SlippageModelConfig(
                    type = it.type.name.lowercase(),
                    baseSlippage = it.baseSlippage,
                    volumeImpact = it.volumeImpact
                )
            }
        )
    }

    // ============================================================================
    // Legacy 메서드 (하위 호환성)
    // ============================================================================

    /**
     * 백테스트 요청 (Legacy)
     * @deprecated Use runBacktest instead
     */
    @Deprecated("Use runBacktest instead", ReplaceWith("runBacktest(request, userId)"))
    fun requestBacktest(request: BacktestRequestDto, userId: String?): BacktestResponse {
        val requestId = UUID.randomUUID().toString()
        val timestamp = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toString()

        logger.info("백테스트 요청 시작: requestId=$requestId, strategyId=${request.strategyId}")

        val backtestRequest = BacktestRequest(
            requestId = requestId,
            strategyId = request.strategyId,
            startDate = request.startDate,
            endDate = request.endDate,
            initialCapital = request.initialCapital,
            timestamp = timestamp,
            source = "quantiq-core",
            userId = userId,
            tickers = request.tickers
        )

        try {
            messagePublisher.publishBacktestRequest(
                topic = EventTopics.BACKTEST_REQUEST,
                request = backtestRequest
            )

            logger.info("백테스트 요청 성공: requestId=$requestId")

            return BacktestResponse(
                success = true,
                requestId = requestId,
                message = "백테스트 요청이 성공적으로 접수되었습니다."
            )
        } catch (e: Exception) {
            logger.error("백테스트 요청 실패: requestId=$requestId", e)

            return BacktestResponse(
                success = false,
                requestId = requestId,
                message = "백테스트 요청에 실패했습니다: ${e.message}"
            )
        }
    }
}

/**
 * 백테스트 예외
 */
class BacktestException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * 백테스트 결과 없음 예외
 */
class BacktestNotFoundException(message: String) : RuntimeException(message)

// ============================================================================
// Legacy DTOs (하위 호환성)
// ============================================================================

/**
 * 백테스트 요청 DTO (Legacy)
 * @deprecated Use BacktestRunRequest instead
 */
@Deprecated("Use BacktestRunRequest instead")
data class BacktestRequestDto(
    val strategyId: Long,
    val startDate: String,          // yyyy-MM-dd
    val endDate: String,            // yyyy-MM-dd
    val initialCapital: BigDecimal,
    val tickers: List<String> = emptyList()  // 백테스트 대상 종목
)

/**
 * 백테스트 응답 DTO (Legacy)
 * @deprecated Use BacktestRunResponse instead
 */
@Deprecated("Use BacktestRunResponse instead")
data class BacktestResponse(
    val success: Boolean,
    val requestId: String,
    val message: String? = null
)
