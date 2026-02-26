package com.quantjumpstock.core.application.marketplace

import com.quantjumpstock.core.domain.model.backtest.BacktestStatus
import com.quantjumpstock.core.domain.model.backtest.BacktestSummary
import com.quantjumpstock.core.domain.model.marketplace.BacktestDetailSummary
import com.quantjumpstock.core.domain.model.marketplace.BacktestTradeDetail
import com.quantjumpstock.core.domain.model.marketplace.EquityCurvePoint
import com.quantjumpstock.core.domain.model.marketplace.MarketplaceStrategy
import com.quantjumpstock.core.domain.model.marketplace.StrategyDetail
import com.quantjumpstock.core.domain.model.strategy.RebalanceFrequency
import com.quantjumpstock.core.domain.model.strategy.StockSelectionType
import com.quantjumpstock.core.domain.port.output.MarketplaceRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.assertThrows
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import org.springframework.cache.CacheManager
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * MarketplaceService 단위 테스트
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("MarketplaceService 단위 테스트")
class MarketplaceServiceTest {

    @Mock
    private lateinit var marketplaceRepository: MarketplaceRepository

    @Mock
    private lateinit var conditionsParser: ConditionsParser

    @Mock
    private lateinit var monthlyReturnsCalculator: MonthlyReturnsCalculator

    @Mock
    private lateinit var cacheManager: CacheManager

    @InjectMocks
    private lateinit var marketplaceService: MarketplaceService

    private lateinit var testStrategy: MarketplaceStrategy
    private lateinit var testBacktestSummary: BacktestSummary

    @BeforeEach
    fun setUp() {
        // 테스트 백테스트 결과 생성
        testBacktestSummary = BacktestSummary(
            id = 1L,
            cagr = BigDecimal("15.5"),
            mdd = BigDecimal("-12.3"),
            sharpeRatio = BigDecimal("1.8"),
            totalReturn = BigDecimal("50.0"),
            volatility = BigDecimal("18.5"),
            winRate = BigDecimal("65.0"),
            startDate = LocalDate.of(2020, 1, 1),
            endDate = LocalDate.of(2023, 12, 31),
            status = BacktestStatus.COMPLETED
        )

        // 테스트 전략 생성
        testStrategy = MarketplaceStrategy(
            id = 1L,
            name = "테스트 전략",
            description = "테스트 설명",
            categoryId = 1L,
            categoryCode = "VALUE",
            categoryName = "가치투자",
            isPremium = false,
            stockSelectionType = StockSelectionType.SCREENING,
            subscriberCount = 100,
            averageRating = BigDecimal("4.5"),
            rebalanceFrequency = RebalanceFrequency.MONTHLY,
            latestBacktest = testBacktestSummary,
            createdAt = LocalDateTime.now()
        )
    }

    @Test
    @DisplayName("공개 전략 목록 조회 - 기본 정렬")
    fun testGetPublicStrategies_DefaultSort() {
        // Given
        val request = StrategyListRequest(
            sortBy = "subscribers",
            page = 0,
            size = 20
        )
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(listOf(testStrategy), pageable, 1)

        whenever(marketplaceRepository.findMarketplaceStrategies(isNull(), isNull(), isNull(), any()))
            .thenReturn(page)

        // When
        val response = marketplaceService.getPublicStrategies(request)

        // Then
        assertNotNull(response)
        assertEquals(1, response.strategies.size)
        assertEquals("테스트 전략", response.strategies[0].name)
        assertEquals(100, response.strategies[0].subscriberCount)
        assertEquals(0, response.pagination.currentPage)
        assertEquals(1L, response.pagination.totalElements)
    }

    @Test
    @DisplayName("공개 전략 목록 조회 - 카테고리 필터링")
    fun testGetPublicStrategies_WithCategoryFilter() {
        // Given
        val request = StrategyListRequest(
            categoryCode = "VALUE",
            sortBy = "subscribers",
            page = 0,
            size = 20
        )
        val page = PageImpl(listOf(testStrategy), PageRequest.of(0, 20), 1)

        whenever(marketplaceRepository.findMarketplaceStrategies(any(), isNull(), isNull(), any()))
            .thenReturn(page)

        // When
        val response = marketplaceService.getPublicStrategies(request)

        // Then
        assertNotNull(response)
        assertEquals(1, response.strategies.size)
        assertEquals("VALUE", response.strategies[0].category.code)
    }

    @Test
    @DisplayName("공개 전략 목록 조회 - CAGR 필터링")
    fun testGetPublicStrategies_WithMinCagr() {
        // Given
        val request = StrategyListRequest(
            minCagr = BigDecimal("10.0"),
            sortBy = "subscribers",
            page = 0,
            size = 20
        )
        val page = PageImpl(listOf(testStrategy), PageRequest.of(0, 20), 1)

        whenever(marketplaceRepository.findMarketplaceStrategies(isNull(), any(), isNull(), any()))
            .thenReturn(page)

        // When
        val response = marketplaceService.getPublicStrategies(request)

        // Then
        assertNotNull(response)
        assertEquals(1, response.strategies.size)
        assertNotNull(response.strategies[0].backtestResult)
        assertEquals(BigDecimal("15.5"), response.strategies[0].backtestResult?.cagr)
    }

    @Test
    @DisplayName("공개 전략 목록 조회 - CAGR 정렬")
    fun testGetPublicStrategies_SortByCagr() {
        // Given
        val request = StrategyListRequest(
            sortBy = "cagr",
            page = 0,
            size = 20
        )
        val page = PageImpl(listOf(testStrategy), PageRequest.of(0, 20), 1)

        whenever(marketplaceRepository.findMarketplaceStrategiesByCagr(isNull(), isNull(), isNull(), any()))
            .thenReturn(page)

        // When
        val response = marketplaceService.getPublicStrategies(request)

        // Then
        assertNotNull(response)
        assertEquals(1, response.strategies.size)
        assertNotNull(response.strategies[0].backtestResult)
    }

    @Test
    @DisplayName("공개 전략 목록 조회 - Sharpe 비율 정렬")
    fun testGetPublicStrategies_SortBySharpe() {
        // Given
        val request = StrategyListRequest(
            sortBy = "sharpe",
            page = 0,
            size = 20
        )
        val page = PageImpl(listOf(testStrategy), PageRequest.of(0, 20), 1)

        whenever(marketplaceRepository.findMarketplaceStrategiesBySharpe(isNull(), isNull(), isNull(), any()))
            .thenReturn(page)

        // When
        val response = marketplaceService.getPublicStrategies(request)

        // Then
        assertNotNull(response)
        assertEquals(1, response.strategies.size)
        assertNotNull(response.strategies[0].backtestResult?.sharpeRatio)
    }

    @Test
    @DisplayName("백테스트 결과가 포함된 응답 검증")
    fun testGetPublicStrategies_BacktestResultIncluded() {
        // Given - 기본 sortBy가 "cagr"이므로 findMarketplaceStrategiesByCagr mock 설정
        val request = StrategyListRequest()
        val page = PageImpl(listOf(testStrategy), PageRequest.of(0, 20), 1)

        whenever(marketplaceRepository.findMarketplaceStrategiesByCagr(isNull(), isNull(), isNull(), any()))
            .thenReturn(page)

        // When
        val response = marketplaceService.getPublicStrategies(request)

        // Then
        val strategy = response.strategies[0]
        assertNotNull(strategy.backtestResult)
        assertEquals(BigDecimal("15.5"), strategy.backtestResult?.cagr)
        assertEquals(BigDecimal("-12.3"), strategy.backtestResult?.mdd)
        assertEquals(BigDecimal("1.8"), strategy.backtestResult?.sharpeRatio)
        assertEquals(BigDecimal("50.0"), strategy.backtestResult?.totalReturn)
        assertEquals("2020-01-01", strategy.backtestResult?.startDate)
        assertEquals("2023-12-31", strategy.backtestResult?.endDate)
    }

    @Test
    @DisplayName("페이징 정보 검증")
    fun testGetPublicStrategies_PaginationInfo() {
        // Given - 기본 sortBy가 "cagr"이므로 findMarketplaceStrategiesByCagr mock 설정
        val request = StrategyListRequest(page = 1, size = 10)
        val page = PageImpl(listOf(testStrategy), PageRequest.of(1, 10), 25)

        whenever(marketplaceRepository.findMarketplaceStrategiesByCagr(isNull(), isNull(), isNull(), any()))
            .thenReturn(page)

        // When
        val response = marketplaceService.getPublicStrategies(request)

        // Then
        assertEquals(1, response.pagination.currentPage)
        assertEquals(10, response.pagination.pageSize)
        assertEquals(25L, response.pagination.totalElements)
        assertEquals(3, response.pagination.totalPages)
        assertEquals(false, response.pagination.isFirst)
        assertEquals(false, response.pagination.isLast)
    }

    // ===== getStrategyDetail 테스트 =====

    @Test
    @DisplayName("전략 상세 조회 - 정상 응답")
    fun testGetStrategyDetail_Success() {
        // Given
        val testDetail = StrategyDetail(
            id = 1L,
            name = "테스트 전략",
            description = "테스트 설명",
            categoryId = 1L,
            categoryCode = "VALUE",
            categoryName = "가치투자",
            isPremium = false,
            stockSelectionType = StockSelectionType.SCREENING,
            subscriberCount = 100,
            averageRating = BigDecimal("4.5"),
            rebalanceFrequency = RebalanceFrequency.MONTHLY,
            latestBacktest = BacktestDetailSummary(
                id = 1L,
                cagr = BigDecimal("15.5"),
                mdd = BigDecimal("-12.3"),
                sharpeRatio = BigDecimal("1.8"),
                sortinoRatio = BigDecimal("2.0"),
                totalReturn = BigDecimal("50.0"),
                volatility = BigDecimal("18.5"),
                winRate = BigDecimal("65.0"),
                totalTrades = 10,
                winningTrades = 7,
                losingTrades = 3,
                avgWin = BigDecimal("5.0"),
                avgLoss = BigDecimal("-3.0"),
                benchmarkReturn = BigDecimal("30.0"),
                alpha = BigDecimal("0.08"),
                beta = BigDecimal("0.95"),
                initialCapital = BigDecimal("10000000"),
                finalValue = BigDecimal("15000000"),
                startDate = LocalDate.of(2020, 1, 1),
                endDate = LocalDate.of(2023, 12, 31),
                equityCurve = listOf(
                    EquityCurvePoint(LocalDate.of(2020, 1, 1), BigDecimal("10000000")),
                    EquityCurvePoint(LocalDate.of(2023, 12, 31), BigDecimal("15000000"))
                ),
                trades = listOf(
                    BacktestTradeDetail(
                        tradeDate = LocalDate.of(2020, 3, 15),
                        ticker = "AAPL",
                        side = "BUY",
                        quantity = 100,
                        price = BigDecimal("150.00"),
                        amount = BigDecimal("15000.00"),
                        pnl = null,
                        pnlPercent = null,
                        holdingDays = null,
                        signalReason = "golden_cross"
                    )
                )
            ),
            currentHoldings = emptyList(),
            conditions = "{}",
            createdAt = LocalDateTime.now()
        )

        whenever(marketplaceRepository.findPublicStrategyById(eq(1L))).thenReturn(testDetail)
        whenever(conditionsParser.parseToRules(any())).thenReturn(emptyList())
        whenever(monthlyReturnsCalculator.calculate(any())).thenReturn(emptyList())

        // When
        val response = marketplaceService.getStrategyDetail(1L)

        // Then
        assertNotNull(response)
        assertEquals("테스트 전략", response.name)
        assertNotNull(response.performanceMetrics)
        assertEquals(BigDecimal("15.5"), response.performanceMetrics?.cagr)
        assertEquals(1, response.trades.size)
        assertEquals("AAPL", response.trades[0].ticker)
        assertEquals("BUY", response.trades[0].side)
    }

    @Test
    @DisplayName("전략 상세 조회 - 존재하지 않는 전략")
    fun testGetStrategyDetail_NotFound() {
        // Given
        whenever(marketplaceRepository.findPublicStrategyById(eq(999L))).thenReturn(null)

        // When & Then
        assertThrows<StrategyNotFoundException> {
            marketplaceService.getStrategyDetail(999L)
        }
    }

    @Test
    @DisplayName("전략 상세 조회 - 백테스트 없는 전략")
    fun testGetStrategyDetail_NoBacktest() {
        // Given
        val testDetail = StrategyDetail(
            id = 2L,
            name = "백테스트 없는 전략",
            description = null,
            categoryId = 1L,
            categoryCode = "MOMENTUM",
            categoryName = "모멘텀",
            isPremium = false,
            stockSelectionType = StockSelectionType.SCREENING,
            subscriberCount = 0,
            averageRating = BigDecimal.ZERO,
            rebalanceFrequency = RebalanceFrequency.DAILY,
            latestBacktest = null,
            currentHoldings = emptyList(),
            conditions = "{}",
            createdAt = LocalDateTime.now()
        )

        whenever(marketplaceRepository.findPublicStrategyById(eq(2L))).thenReturn(testDetail)
        whenever(conditionsParser.parseToRules(any())).thenReturn(emptyList())

        // When
        val response = marketplaceService.getStrategyDetail(2L)

        // Then
        assertNotNull(response)
        assertEquals("백테스트 없는 전략", response.name)
        assertEquals(null, response.performanceMetrics)
        assertTrue(response.trades.isEmpty())
        assertTrue(response.equityCurve.isEmpty())
    }
}
