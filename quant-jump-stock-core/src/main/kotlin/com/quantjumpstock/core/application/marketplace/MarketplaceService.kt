package com.quantjumpstock.core.application.marketplace

import com.quantjumpstock.core.application.strategy.CategoryInfo
import com.quantjumpstock.core.domain.model.marketplace.MarketplaceStrategy
import com.quantjumpstock.core.domain.model.marketplace.StrategyDetail
import com.quantjumpstock.core.domain.port.output.MarketplaceRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Marketplace 서비스
 * 공개 전략 목록 조회 및 관리
 */
@Service
@Transactional(readOnly = true)
class MarketplaceService(
    private val marketplaceRepository: MarketplaceRepository,
    private val conditionsParser: ConditionsParser,
    private val monthlyReturnsCalculator: MonthlyReturnsCalculator
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 공개 전략 목록 조회
     * 필터링 및 정렬 지원
     * 캐싱: 6시간 TTL, 분석 스케줄러 완료 시 초기화
     */
    @Cacheable(
        value = ["marketplaceStrategies"],
        key = "#request.categoryCode + ':' + #request.minCagr + ':' + #request.maxMdd + ':' + #request.sortBy + ':' + #request.page + ':' + #request.size"
    )
    fun getPublicStrategies(request: StrategyListRequest): StrategyListResponse {
        logger.info("공개 전략 목록 조회: categoryCode=${request.categoryCode}, minCagr=${request.minCagr}, maxMdd=${request.maxMdd}, sortBy=${request.sortBy}")

        val page = when (request.sortBy?.lowercase()) {
            "cagr" -> marketplaceRepository.findMarketplaceStrategiesByCagr(
                request.categoryCode,
                request.minCagr,
                request.maxMdd,
                request.toPageable()
            )
            "sharpe" -> marketplaceRepository.findMarketplaceStrategiesBySharpe(
                request.categoryCode,
                request.minCagr,
                request.maxMdd,
                request.toPageable()
            )
            else -> marketplaceRepository.findMarketplaceStrategies(
                request.categoryCode,
                request.minCagr,
                request.maxMdd,
                request.toPageable()
            )
        }

        val strategies = page.content.map { it.toDto() }.toMutableList()
        val pagination = page.toPaginationDto()

        logger.info("공개 전략 목록 조회 완료: 총 ${pagination.totalElements}개, 현재 페이지 ${pagination.currentPage + 1}/${pagination.totalPages}")

        return StrategyListResponse(
            strategies = strategies,
            pagination = pagination
        )
    }

    /**
     * 전략 상세 조회
     * 성과 지표, 수익 곡선, 현재 보유 종목 포함
     * 캐싱: 6시간 TTL, 분석 스케줄러 완료 시 초기화
     */
    @Cacheable(value = ["strategyDetail"], key = "#id")
    fun getStrategyDetail(id: Long): StrategyDetailResponse {
        logger.info("전략 상세 조회: id=$id")

        val strategyDetail = marketplaceRepository.findPublicStrategyById(id)
            ?: throw StrategyNotFoundException("전략을 찾을 수 없습니다: id=$id")

        logger.info("전략 상세 조회 완료: ${strategyDetail.name}")

        return strategyDetail.toDetailResponse()
    }

    /**
     * StrategyDetail을 StrategyDetailResponse로 변환
     */
    private fun StrategyDetail.toDetailResponse(): StrategyDetailResponse {
        return StrategyDetailResponse(
            id = this.id,
            name = this.name,
            description = this.description,
            category = CategoryInfo(
                id = this.categoryId,
                code = this.categoryCode,
                name = this.categoryName
            ),
            isPremium = this.isPremium,
            stockSelectionType = this.stockSelectionType,
            subscriberCount = this.subscriberCount,
            averageRating = this.averageRating,
            rebalanceFrequency = this.rebalanceFrequency.name,
            performanceMetrics = this.latestBacktest?.let {
                PerformanceMetricsDto(
                    cagr = it.cagr,
                    mdd = it.mdd,
                    sharpeRatio = it.sharpeRatio,
                    sortinoRatio = it.sortinoRatio,
                    totalReturn = it.totalReturn,
                    volatility = it.volatility,
                    winRate = it.winRate,
                    totalTrades = it.totalTrades,
                    winningTrades = it.winningTrades,
                    losingTrades = it.losingTrades,
                    avgWin = it.avgWin,
                    avgLoss = it.avgLoss,
                    benchmarkReturn = it.benchmarkReturn,
                    alpha = it.alpha,
                    beta = it.beta,
                    initialCapital = it.initialCapital,
                    finalValue = it.finalValue,
                    startDate = it.startDate.toString(),
                    endDate = it.endDate.toString()
                )
            },
            equityCurve = this.latestBacktest?.equityCurve?.map {
                EquityCurvePointDto(
                    date = it.date.toString(),
                    value = it.value
                )
            }?.toMutableList() ?: mutableListOf(),
            currentHoldings = this.currentHoldings.map {
                CurrentHoldingDto(
                    ticker = it.ticker,
                    targetWeight = it.targetWeight,
                    signalDate = it.signalDate.toString(),
                    reason = it.reason
                )
            }.toMutableList(),
            rules = conditionsParser.parseToRules(this.conditions).toMutableList(),
            monthlyReturns = this.latestBacktest?.equityCurve?.let {
                monthlyReturnsCalculator.calculate(it)
            }?.toMutableList() ?: mutableListOf(),
            trades = this.latestBacktest?.trades?.map {
                BacktestTradeDto(
                    tradeDate = it.tradeDate.toString(),
                    ticker = it.ticker,
                    side = it.side,
                    quantity = it.quantity,
                    price = it.price,
                    amount = it.amount,
                    pnl = it.pnl,
                    pnlPercent = it.pnlPercent,
                    holdingDays = it.holdingDays,
                    signalReason = it.signalReason
                )
            }?.toMutableList() ?: mutableListOf(),
            createdAt = this.createdAt,
            // SCRUM-344: 유니버스 + 대표 백테스트
            recommendedUniverseType = this.recommendedUniverseType,
            supportedUniverseTypes = this.supportedUniverseTypes.toMutableList()
        )
    }

    /**
     * MarketplaceStrategy를 StrategyDto로 변환
     */
    private fun MarketplaceStrategy.toDto(): StrategyDto {
        return StrategyDto(
            id = this.id,
            name = this.name,
            description = this.description,
            category = CategoryInfo(
                id = this.categoryId,
                code = this.categoryCode,
                name = this.categoryName
            ),
            isPremium = this.isPremium,
            stockSelectionType = this.stockSelectionType,
            subscriberCount = this.subscriberCount,
            averageRating = this.averageRating,
            rebalanceFrequency = this.rebalanceFrequency.name,
            backtestResult = this.latestBacktest?.let {
                BacktestResultDto(
                    cagr = it.cagr,
                    mdd = it.mdd,
                    sharpeRatio = it.sharpeRatio,
                    totalReturn = it.totalReturn,
                    volatility = it.volatility,
                    winRate = it.winRate,
                    startDate = it.startDate.toString(),
                    endDate = it.endDate.toString()
                )
            },
            createdAt = this.createdAt,
            recommendedUniverseType = this.recommendedUniverseType,
            supportedUniverseTypes = this.supportedUniverseTypes?.toMutableList()
        )
    }

    /**
     * Page를 PaginationDto로 변환
     */
    private fun Page<*>.toPaginationDto(): PaginationDto {
        return PaginationDto(
            currentPage = this.number,
            pageSize = this.size,
            totalElements = this.totalElements,
            totalPages = this.totalPages,
            isFirst = this.isFirst,
            isLast = this.isLast
        )
    }
}
