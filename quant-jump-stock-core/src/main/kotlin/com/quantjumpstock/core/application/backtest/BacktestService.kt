package com.quantjumpstock.core.application.backtest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestResultEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestResultJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.BacktestTradeEntity
import com.quantjumpstock.core.domain.economic.port.output.MessagePublisher
import com.quantjumpstock.core.domain.model.*
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
    private val backtestResultRepository: BacktestResultJpaRepository,
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
        val entity = backtestResultRepository.findByIdWithTrades(id)
            .orElseThrow { BacktestNotFoundException("백테스트 결과를 찾을 수 없습니다: id=$id") }

        return mapToResultResponse(entity)
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
        val pageable = PageRequest.of(page, size.coerceAtMost(100), Sort.by(Sort.Direction.DESC, "createdAt"))

        val pageResult = when {
            strategyId != null -> backtestResultRepository.findByStrategyIdOrderByCreatedAtDesc(strategyId, pageable)
            userId != null -> backtestResultRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            else -> backtestResultRepository.findAll(pageable)
        }

        val content = pageResult.content.map { mapToListItemResponse(it) }

        return PagedResponse(
            content = content,
            page = page,
            size = size,
            totalElements = pageResult.totalElements,
            totalPages = pageResult.totalPages
        )
    }

    /**
     * 백테스트 상태 확인
     */
    fun getBacktestStatus(id: Long): String {
        val entity = backtestResultRepository.findById(id)
            .orElseThrow { BacktestNotFoundException("백테스트 결과를 찾을 수 없습니다: id=$id") }
        return entity.status.name
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
            5
        }
        return baseTime + tickerTime + (periodYears * 5)
    }

    /**
     * Entity → ResultResponse 매핑
     */
    private fun mapToResultResponse(entity: BacktestResultEntity): BacktestResultResponse {
        val equityCurve = entity.equityCurve?.let { parseEquityCurve(it) }

        return BacktestResultResponse(
            id = entity.id!!,
            strategyId = entity.strategy.id!!,
            strategyName = entity.strategy.name,
            status = entity.status.name,
            metrics = BacktestMetrics(
                cagr = entity.cagr,
                mdd = entity.mdd,
                sharpeRatio = entity.sharpeRatio,
                sortinoRatio = entity.sortinoRatio,
                winRate = entity.winRate,
                totalReturn = entity.totalReturn,
                volatility = entity.volatility,
                totalTrades = entity.totalTrades,
                winningTrades = entity.winningTrades,
                losingTrades = entity.losingTrades,
                avgWin = entity.avgWin,
                avgLoss = entity.avgLoss,
                benchmarkReturn = entity.benchmarkReturn,
                alpha = entity.alpha,
                beta = entity.beta
            ),
            equityCurve = equityCurve,
            benchmarkCurve = null, // TODO: benchmarkCurve 필드 추가 시 매핑
            trades = entity.trades.map { mapToTradeResponse(it) },
            createdAt = entity.createdAt,
            completedAt = entity.completedAt
        )
    }

    /**
     * Entity → ListItemResponse 매핑
     */
    private fun mapToListItemResponse(entity: BacktestResultEntity): BacktestListItemResponse {
        return BacktestListItemResponse(
            id = entity.id!!,
            strategyId = entity.strategy.id!!,
            strategyName = entity.strategy.name,
            status = entity.status.name,
            startDate = entity.startDate,
            endDate = entity.endDate,
            initialCapital = entity.initialCapital,
            finalValue = entity.finalValue,
            totalReturn = entity.totalReturn,
            cagr = entity.cagr,
            mdd = entity.mdd,
            sharpeRatio = entity.sharpeRatio,
            createdAt = entity.createdAt,
            completedAt = entity.completedAt
        )
    }

    /**
     * TradeEntity → TradeResponse 매핑
     */
    private fun mapToTradeResponse(entity: BacktestTradeEntity): BacktestTradeResponse {
        return BacktestTradeResponse(
            id = entity.id!!,
            tradeDate = entity.tradeDate,
            ticker = entity.ticker,
            side = entity.side.name,
            quantity = entity.quantity,
            price = entity.price,
            amount = entity.amount,
            commission = entity.commission,
            pnl = entity.pnl,
            pnlPercent = entity.pnlPercent,
            holdingDays = entity.holdingDays,
            signalReason = entity.signalReason
        )
    }

    /**
     * JSON 문자열 → EquityCurve 파싱
     */
    private fun parseEquityCurve(json: String): List<EquityCurvePoint>? {
        return try {
            objectMapper.readValue<List<Map<String, Any>>>(json).map { point ->
                EquityCurvePoint(
                    date = point["date"]?.toString() ?: "",
                    value = BigDecimal(point["value"]?.toString() ?: "0")
                )
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
