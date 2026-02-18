package com.quantjumpstock.core.application.backtest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.quantjumpstock.core.domain.model.backtest.BacktestResult
import com.quantjumpstock.core.domain.model.backtest.BacktestStatus
import com.quantjumpstock.core.domain.model.backtest.BacktestType
import com.quantjumpstock.core.domain.model.backtest.UniverseType
import com.quantjumpstock.core.domain.port.output.BacktestResultRepository
import com.quantjumpstock.core.domain.economic.port.output.MessagePublisher
import com.quantjumpstock.core.domain.model.BacktestRequest
import com.quantjumpstock.core.events.EventTopics
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * BacktestService 단위 테스트
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("BacktestService 단위 테스트")
class BacktestServiceTest {

    @Mock
    private lateinit var messagePublisher: MessagePublisher

    @Mock
    private lateinit var backtestResultRepository: BacktestResultRepository

    private lateinit var objectMapper: ObjectMapper

    private lateinit var backtestService: BacktestService

    @BeforeEach
    fun setUp() {
        objectMapper = jacksonObjectMapper()
        backtestService = BacktestService(messagePublisher, backtestResultRepository, objectMapper)
    }

    private fun savedPlaceholder(strategyId: Long = 1L, userId: String? = null): BacktestResult =
        BacktestResult(
            id = 100L,
            strategyId = strategyId,
            userId = userId?.toLongOrNull(),
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2024, 12, 31),
            initialCapital = BigDecimal("10000000"),
            finalValue = BigDecimal.ZERO,
            totalReturn = BigDecimal.ZERO,
            cagr = BigDecimal.ZERO,
            mdd = BigDecimal.ZERO,
            universeType = UniverseType.MARKET,
            backtestType = BacktestType.USER_CUSTOM,
            status = BacktestStatus.RUNNING,
            createdAt = LocalDateTime.now()
        )

    private fun makeRequest(
        strategyId: Long = 1L,
        startDate: String = "2024-01-01",
        endDate: String = "2024-12-31",
        capital: BigDecimal = BigDecimal("10000000"),
        tickers: List<String> = listOf("AAPL")
    ) = BacktestRunRequest(
        strategyId = strategyId,
        startDate = startDate,
        endDate = endDate,
        initialCapital = capital,
        tickers = tickers
    )

    @Test
    @DisplayName("백테스트 요청 성공")
    fun testRunBacktest_Success() {
        // Given
        val request = makeRequest(strategyId = 1L)
        val userId = "100"

        whenever(backtestResultRepository.save(any())).thenReturn(savedPlaceholder(1L, userId))
        doNothing().`when`(messagePublisher).publishBacktestRequest(
            eq(EventTopics.BACKTEST_REQUEST),
            any()
        )

        // When
        val response = backtestService.runBacktest(request, userId)

        // Then
        assertNotNull(response.backtestId)
        assertEquals("PENDING", response.status)
        assertTrue(response.message.contains("시작"))

        // Verify Pub/Sub message published
        verify(messagePublisher).publishBacktestRequest(
            eq(EventTopics.BACKTEST_REQUEST),
            argThat<BacktestRequest> { req -> req.strategyId == 1L && req.userId == userId }
        )
    }

    @Test
    @DisplayName("백테스트 요청 성공 - userId 없음")
    fun testRunBacktest_SuccessWithoutUserId() {
        // Given
        val request = makeRequest(strategyId = 2L)

        whenever(backtestResultRepository.save(any())).thenReturn(savedPlaceholder(2L))
        doNothing().`when`(messagePublisher).publishBacktestRequest(
            eq(EventTopics.BACKTEST_REQUEST),
            any()
        )

        // When
        val response = backtestService.runBacktest(request, null)

        // Then
        assertNotNull(response.backtestId)
        assertEquals("PENDING", response.status)

        // Verify null userId
        verify(messagePublisher).publishBacktestRequest(
            eq(EventTopics.BACKTEST_REQUEST),
            argThat<BacktestRequest> { req -> req.strategyId == 2L && req.userId == null }
        )
    }

    @Test
    @DisplayName("백테스트 요청 실패 - Pub/Sub 발행 실패 시 BacktestException 발생")
    fun testRunBacktest_PublishFailure() {
        // Given
        val request = makeRequest()

        whenever(backtestResultRepository.save(any())).thenReturn(savedPlaceholder())
        doThrow(RuntimeException("Pub/Sub connection failed"))
            .`when`(messagePublisher).publishBacktestRequest(
                eq(EventTopics.BACKTEST_REQUEST),
                any()
            )

        // When / Then
        assertThrows<BacktestException> {
            backtestService.runBacktest(request, "100")
        }
    }

    @Test
    @DisplayName("백테스트 요청 - requestId 고유성 검증")
    fun testRunBacktest_UniqueRequestId() {
        // Given
        val request = makeRequest()

        whenever(backtestResultRepository.save(any())).thenReturn(savedPlaceholder())
        doNothing().`when`(messagePublisher).publishBacktestRequest(any(), any())

        // When
        val response1 = backtestService.runBacktest(request, null)
        val response2 = backtestService.runBacktest(request, null)

        // Then
        assertTrue(response1.backtestId != response2.backtestId, "각 요청은 고유한 requestId를 가져야 합니다")
    }
}
