package com.quantjumpstock.core.application.marketplace

import com.quantjumpstock.core.adapter.output.persistence.jpa.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.ArgumentMatchers.any as mockAny
import org.mockito.ArgumentMatchers.isNull as mockIsNull
import org.mockito.Mockito.`when` as whenever
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

/**
 * MarketplaceService 단위 테스트
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("MarketplaceService 단위 테스트")
class MarketplaceServiceTest {

    @Mock
    private lateinit var strategyRepository: StrategyJpaRepository

    @InjectMocks
    private lateinit var marketplaceService: MarketplaceService

    private lateinit var testStrategy: StrategyEntity
    private lateinit var testBacktestResult: BacktestResultEntity

    @BeforeEach
    fun setUp() {
        // 테스트 전략 생성
        testStrategy = StrategyEntity(
            id = 1L,
            name = "테스트 전략",
            description = "테스트 설명",
            category = StrategyCategory.VALUE,
            isPublic = true,
            isPremium = false,
            status = StrategyStatus.ACTIVE,
            subscriberCount = 100,
            averageRating = BigDecimal("4.5"),
            rebalanceFrequency = RebalanceFrequency.MONTHLY,
            createdAt = LocalDateTime.now()
        )

        // 테스트 백테스트 결과 생성
        testBacktestResult = BacktestResultEntity(
            id = 1L,
            strategy = testStrategy,
            startDate = LocalDate.of(2020, 1, 1),
            endDate = LocalDate.of(2023, 12, 31),
            initialCapital = BigDecimal("10000000"),
            finalValue = BigDecimal("15000000"),
            totalReturn = BigDecimal("50.0"),
            cagr = BigDecimal("15.5"),
            mdd = BigDecimal("-12.3"),
            sharpeRatio = BigDecimal("1.8"),
            volatility = BigDecimal("18.5"),
            winRate = BigDecimal("65.0"),
            status = BacktestStatus.COMPLETED,
            createdAt = LocalDateTime.now()
        )

        // 전략에 백테스트 결과 추가
        testStrategy.backtestResults.add(testBacktestResult)
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

        whenever(strategyRepository.findMarketplaceStrategies(mockIsNull(), mockIsNull(), mockIsNull(), mockAny(Pageable::class.java)))
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
            category = StrategyCategory.VALUE,
            sortBy = "subscribers",
            page = 0,
            size = 20
        )
        val page = PageImpl(listOf(testStrategy), PageRequest.of(0, 20), 1)

        whenever(strategyRepository.findMarketplaceStrategies(mockAny(StrategyCategory::class.java), mockIsNull(), mockIsNull(), mockAny(Pageable::class.java)))
            .thenReturn(page)

        // When
        val response = marketplaceService.getPublicStrategies(request)

        // Then
        assertNotNull(response)
        assertEquals(1, response.strategies.size)
        assertEquals(StrategyCategory.VALUE, response.strategies[0].category)
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

        whenever(strategyRepository.findMarketplaceStrategies(mockIsNull(), mockAny(BigDecimal::class.java), mockIsNull(), mockAny(Pageable::class.java)))
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

        whenever(strategyRepository.findMarketplaceStrategiesByCagr(mockIsNull(), mockIsNull(), mockIsNull(), mockAny(Pageable::class.java)))
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

        whenever(strategyRepository.findMarketplaceStrategiesBySharpe(mockIsNull(), mockIsNull(), mockIsNull(), mockAny(Pageable::class.java)))
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
        // Given
        val request = StrategyListRequest()
        val page = PageImpl(listOf(testStrategy), PageRequest.of(0, 20), 1)

        whenever(strategyRepository.findMarketplaceStrategies(mockIsNull(), mockIsNull(), mockIsNull(), mockAny(Pageable::class.java)))
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
        // Given
        val request = StrategyListRequest(page = 1, size = 10)
        val page = PageImpl(listOf(testStrategy), PageRequest.of(1, 10), 25)

        whenever(strategyRepository.findMarketplaceStrategies(mockIsNull(), mockIsNull(), mockIsNull(), mockAny(Pageable::class.java)))
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
}
