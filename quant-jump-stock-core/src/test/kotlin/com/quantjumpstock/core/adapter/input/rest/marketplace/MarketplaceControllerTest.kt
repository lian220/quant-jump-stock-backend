package com.quantjumpstock.core.adapter.input.rest.marketplace

import com.quantjumpstock.core.adapter.output.persistence.jpa.*
import com.quantjumpstock.core.application.marketplace.MarketplaceService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Marketplace Controller 통합 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Marketplace API 통합 테스트")
class MarketplaceControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var strategyRepository: StrategyJpaRepository

    @Autowired
    private lateinit var userRepository: UserJpaRepository

    @Autowired
    private lateinit var backtestResultRepository: BacktestResultJpaRepository

    @Autowired
    private lateinit var marketplaceService: MarketplaceService

    private lateinit var testUser: UserEntity
    private lateinit var testStrategy1: StrategyEntity
    private lateinit var testStrategy2: StrategyEntity

    @BeforeEach
    fun setUp() {
        // 테스트 사용자 생성
        testUser = userRepository.save(
            UserEntity(
                userId = "test_user",
                name = "테스트 사용자",
                email = "test@example.com",
                passwordHash = "hashed_password"
            )
        )

        // 테스트 전략 1 (VALUE, 높은 CAGR)
        testStrategy1 = strategyRepository.save(
            StrategyEntity(
                name = "가치투자 전략",
                description = "저평가 종목 투자",
                category = StrategyCategory.VALUE,
                owner = testUser,
                isPublic = true,
                isPremium = false,
                status = StrategyStatus.ACTIVE,
                subscriberCount = 100,
                averageRating = BigDecimal("4.5"),
                rebalanceFrequency = RebalanceFrequency.MONTHLY
            )
        )

        // 백테스트 결과 1
        backtestResultRepository.save(
            BacktestResultEntity(
                strategy = testStrategy1,
                user = testUser,
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
                status = BacktestStatus.COMPLETED
            )
        )

        // 테스트 전략 2 (MOMENTUM, 낮은 CAGR)
        testStrategy2 = strategyRepository.save(
            StrategyEntity(
                name = "모멘텀 전략",
                description = "상승 추세 종목 투자",
                category = StrategyCategory.MOMENTUM,
                owner = testUser,
                isPublic = true,
                isPremium = false,
                status = StrategyStatus.ACTIVE,
                subscriberCount = 50,
                averageRating = BigDecimal("4.0"),
                rebalanceFrequency = RebalanceFrequency.WEEKLY
            )
        )

        // 백테스트 결과 2
        backtestResultRepository.save(
            BacktestResultEntity(
                strategy = testStrategy2,
                user = testUser,
                startDate = LocalDate.of(2020, 1, 1),
                endDate = LocalDate.of(2023, 12, 31),
                initialCapital = BigDecimal("10000000"),
                finalValue = BigDecimal("12000000"),
                totalReturn = BigDecimal("20.0"),
                cagr = BigDecimal("8.5"),
                mdd = BigDecimal("-20.0"),
                sharpeRatio = BigDecimal("1.2"),
                volatility = BigDecimal("25.0"),
                winRate = BigDecimal("55.0"),
                status = BacktestStatus.COMPLETED
            )
        )
    }

    @Test
    @DisplayName("공개 전략 목록 조회 - 기본 정렬 (구독자 수)")
    fun testGetPublicStrategies_DefaultSort() {
        mockMvc.perform(get("/api/v1/marketplace/strategies"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.strategies").isArray)
            .andExpect(jsonPath("$.strategies[0].subscriberCount").value(100))
            .andExpect(jsonPath("$.pagination.currentPage").value(0))
            .andExpect(jsonPath("$.pagination.totalElements").exists())
    }

    @Test
    @DisplayName("공개 전략 목록 조회 - 카테고리 필터링")
    fun testGetPublicStrategies_FilterByCategory() {
        mockMvc.perform(
            get("/api/v1/marketplace/strategies")
                .param("category", "VALUE")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.strategies").isArray)
            .andExpect(jsonPath("$.strategies[0].category").value("VALUE"))
            .andExpect(jsonPath("$.strategies[0].name").value("가치투자 전략"))
    }

    @Test
    @DisplayName("공개 전략 목록 조회 - CAGR 필터링")
    fun testGetPublicStrategies_FilterByMinCagr() {
        mockMvc.perform(
            get("/api/v1/marketplace/strategies")
                .param("minCagr", "10.0")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.strategies").isArray)
            .andExpect(jsonPath("$.strategies[0].backtestResult.cagr").exists())
    }

    @Test
    @DisplayName("공개 전략 목록 조회 - MDD 필터링")
    fun testGetPublicStrategies_FilterByMaxMdd() {
        mockMvc.perform(
            get("/api/v1/marketplace/strategies")
                .param("maxMdd", "-15.0")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.strategies").isArray)
    }

    @Test
    @DisplayName("공개 전략 목록 조회 - CAGR 정렬")
    fun testGetPublicStrategies_SortByCagr() {
        mockMvc.perform(
            get("/api/v1/marketplace/strategies")
                .param("sortBy", "cagr")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.strategies").isArray)
            .andExpect(jsonPath("$.strategies[0].backtestResult.cagr").exists())
    }

    @Test
    @DisplayName("공개 전략 목록 조회 - Sharpe 비율 정렬")
    fun testGetPublicStrategies_SortBySharpe() {
        mockMvc.perform(
            get("/api/v1/marketplace/strategies")
                .param("sortBy", "sharpe")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.strategies").isArray)
            .andExpect(jsonPath("$.strategies[0].backtestResult.sharpeRatio").exists())
    }

    @Test
    @DisplayName("공개 전략 목록 조회 - 최신순 정렬")
    fun testGetPublicStrategies_SortByRecent() {
        mockMvc.perform(
            get("/api/v1/marketplace/strategies")
                .param("sortBy", "recent")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.strategies").isArray)
    }

    @Test
    @DisplayName("공개 전략 목록 조회 - 페이징")
    fun testGetPublicStrategies_Pagination() {
        mockMvc.perform(
            get("/api/v1/marketplace/strategies")
                .param("page", "0")
                .param("size", "1")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.strategies.length()").value(1))
            .andExpect(jsonPath("$.pagination.pageSize").value(1))
            .andExpect(jsonPath("$.pagination.isFirst").value(true))
    }

    @Test
    @DisplayName("공개 전략 목록 조회 - 복합 필터링")
    fun testGetPublicStrategies_MultipleFilters() {
        mockMvc.perform(
            get("/api/v1/marketplace/strategies")
                .param("category", "VALUE")
                .param("minCagr", "10.0")
                .param("maxMdd", "-15.0")
                .param("sortBy", "cagr")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.strategies").isArray)
    }
}
