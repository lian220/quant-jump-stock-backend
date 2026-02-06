package com.quantjumpstock.core.application.backtest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.quantjumpstock.core.domain.port.output.BacktestResultRepository
import com.quantjumpstock.core.domain.economic.port.output.MessagePublisher
import com.quantjumpstock.core.domain.model.BacktestRequest
import com.quantjumpstock.core.events.EventTopics
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

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

    @Test
    @DisplayName("백테스트 요청 성공")
    fun testRequestBacktest_Success() {
        // Given
        val request = BacktestRequestDto(
            strategyId = 1L,
            startDate = "2024-01-01",
            endDate = "2024-12-31",
            initialCapital = BigDecimal("10000000")
        )
        val userId = "user-123"

        doNothing().`when`(messagePublisher).publishBacktestRequest(
            eq(EventTopics.BACKTEST_REQUEST),
            any()
        )

        // When
        val response = backtestService.requestBacktest(request, userId)

        // Then
        assertTrue(response.success)
        assertNotNull(response.requestId)
        assertEquals("백테스트 요청이 성공적으로 접수되었습니다.", response.message)

        // Verify Kafka message published
        verify(messagePublisher).publishBacktestRequest(
            eq(EventTopics.BACKTEST_REQUEST),
            argThat<BacktestRequest> { backtestRequest ->
                backtestRequest.strategyId == 1L &&
                backtestRequest.startDate == "2024-01-01" &&
                backtestRequest.endDate == "2024-12-31" &&
                backtestRequest.initialCapital == BigDecimal("10000000") &&
                backtestRequest.userId == "user-123"
            }
        )
    }

    @Test
    @DisplayName("백테스트 요청 성공 - userId 없음")
    fun testRequestBacktest_SuccessWithoutUserId() {
        // Given
        val request = BacktestRequestDto(
            strategyId = 2L,
            startDate = "2023-06-01",
            endDate = "2023-12-31",
            initialCapital = BigDecimal("5000000")
        )

        doNothing().`when`(messagePublisher).publishBacktestRequest(
            eq(EventTopics.BACKTEST_REQUEST),
            any()
        )

        // When
        val response = backtestService.requestBacktest(request, null)

        // Then
        assertTrue(response.success)
        assertNotNull(response.requestId)

        // Verify Kafka message published with null userId
        verify(messagePublisher).publishBacktestRequest(
            eq(EventTopics.BACKTEST_REQUEST),
            argThat<BacktestRequest> { backtestRequest ->
                backtestRequest.strategyId == 2L &&
                backtestRequest.userId == null
            }
        )
    }

    @Test
    @DisplayName("백테스트 요청 실패 - Kafka 발행 실패")
    fun testRequestBacktest_KafkaPublishFailure() {
        // Given
        val request = BacktestRequestDto(
            strategyId = 1L,
            startDate = "2024-01-01",
            endDate = "2024-12-31",
            initialCapital = BigDecimal("10000000")
        )

        doThrow(RuntimeException("Kafka connection failed"))
            .`when`(messagePublisher).publishBacktestRequest(
                eq(EventTopics.BACKTEST_REQUEST),
                any()
            )

        // When
        val response = backtestService.requestBacktest(request, "user-123")

        // Then
        assertFalse(response.success)
        assertNotNull(response.requestId)
        assertTrue(response.message?.contains("실패") == true)
    }

    @Test
    @DisplayName("백테스트 요청 - requestId 고유성 검증")
    fun testRequestBacktest_UniqueRequestId() {
        // Given
        val request = BacktestRequestDto(
            strategyId = 1L,
            startDate = "2024-01-01",
            endDate = "2024-12-31",
            initialCapital = BigDecimal("10000000")
        )

        doNothing().`when`(messagePublisher).publishBacktestRequest(
            eq(EventTopics.BACKTEST_REQUEST),
            any()
        )

        // When
        val response1 = backtestService.requestBacktest(request, null)
        val response2 = backtestService.requestBacktest(request, null)

        // Then
        assertTrue(response1.success)
        assertTrue(response2.success)
        assertTrue(response1.requestId != response2.requestId, "각 요청은 고유한 requestId를 가져야 합니다")
    }
}
