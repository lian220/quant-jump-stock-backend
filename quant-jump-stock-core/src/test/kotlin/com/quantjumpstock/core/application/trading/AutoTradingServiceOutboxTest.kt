package com.quantjumpstock.core.application.trading

import com.quantjumpstock.core.domain.model.prediction.CompositeGrade
import com.quantjumpstock.core.domain.model.prediction.PredictionResult
import com.quantjumpstock.core.domain.model.trading.Account
import com.quantjumpstock.core.domain.model.trading.Trade
import com.quantjumpstock.core.domain.model.trading.TradeSide
import com.quantjumpstock.core.domain.model.trading.TradeStatus
import com.quantjumpstock.core.domain.model.trading.TradingConfig
import com.quantjumpstock.core.domain.model.user.User
import com.quantjumpstock.core.domain.port.output.AccountRepository
import com.quantjumpstock.core.domain.port.output.StrategyDefaultStockRepository
import com.quantjumpstock.core.domain.port.output.StrategySubscriptionRepository
import com.quantjumpstock.core.domain.port.output.TradeRepository
import com.quantjumpstock.core.domain.port.output.TradeSignalExecutedRepository
import com.quantjumpstock.core.domain.port.output.TradingConfigRepository
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.domain.prediction.port.output.PredictionResultRepositoryPort
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Phase 1A PRE Task 11 — AutoTradingService 트랜잭션-외부I/O 분리 검증 (Option B 경량판).
 *
 * Step 1 시점에서는 service 가 KIS placeOrder 를 직접 호출하므로 본 테스트는 실패해야 한다
 * (정확히는 ApplicationEventPublisher 가 아직 service 의존성에 추가되지 않아 컴파일 실패).
 *
 * Step 3 이후: executeAutoTrading 은 PENDING Trade 저장 + OrderExecutionRequestEvent 발행만
 * 수행하고 KIS 호출은 @TransactionalEventListener(AFTER_COMMIT) 리스너로 위임된다.
 */
class AutoTradingServiceOutboxTest {

    private val userRepository = mockk<UserRepository>()
    private val tradingConfigRepository = mockk<TradingConfigRepository>()
    private val predictionResultRepository = mockk<PredictionResultRepositoryPort>()
    private val tradeRepository = mockk<TradeRepository>()
    private val tradeSignalExecutedRepository = mockk<TradeSignalExecutedRepository>(relaxed = true)
    private val accountRepository = mockk<AccountRepository>()
    private val strategySubscriptionRepository = mockk<StrategySubscriptionRepository>()
    private val strategyDefaultStockRepository = mockk<StrategyDefaultStockRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>()
    private val stalePendingTradeReconciler = mockk<StalePendingTradeReconciler>(relaxed = true)

    private val service = AutoTradingService(
        userRepository,
        tradingConfigRepository,
        predictionResultRepository,
        tradeRepository,
        tradeSignalExecutedRepository,
        accountRepository,
        strategySubscriptionRepository,
        strategyDefaultStockRepository,
        eventPublisher,
        stalePendingTradeReconciler
    )

    @Test
    fun `executeAutoTrading 는 KIS placeOrder 를 직접 호출하지 않고 OrderExecutionRequestEvent 를 발행한다`() {
        val userId = 1L
        val user = User(id = userId, userId = "trader-1", email = "t@example.com")
        val config = TradingConfig(
            id = 1L,
            userId = userId,
            enabled = true,
            autoTradingEnabled = true,
            minCompositeScore = BigDecimal("2.0"),
            maxStocksToBuy = 5,
            maxAmountPerStock = BigDecimal("10000")
        )
        val account = Account(id = 1L, userId = userId, cash = BigDecimal("100000"))
        val prediction = PredictionResult(
            ticker = "AAPL",
            stockName = "Apple",
            analysisDate = LocalDate.now().minusDays(1),
            aiPredictedPrice = BigDecimal("100"),
            compositeScore = BigDecimal("3.0"),
            compositeGrade = CompositeGrade.B
        )

        every { predictionResultRepository.findHighConfidenceBuySignals(any(), 2.0) } returns listOf(prediction)
        every { tradingConfigRepository.findAllEnabledWithAutoTrading() } returns listOf(config)
        every { userRepository.findById(userId) } returns user
        every { accountRepository.findByUserId(userId) } returns account
        every { strategySubscriptionRepository.findActiveByUserId(userId) } returns emptyList()
        every {
            tradeRepository.findRecentTrades(any(), any(), TradeSide.BUY, TradeStatus.PENDING, any())
        } returns emptyList()
        every { accountRepository.save(any()) } answers { firstArg() }
        every { tradeRepository.save(any()) } answers { firstArg<Trade>().copy(id = 99L) }

        val eventSlot = slot<OrderExecutionRequestEvent>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } just Runs

        service.executeAutoTrading()

        // KIS placeOrder 가 service 내부에서 호출되지 않음은 의존성 자체가 제거되어 컴파일 단에서 보장된다.
        // 본 테스트는 PENDING Trade 저장 + 이벤트 발행만으로 흐름이 마무리됨을 검증한다.
        verify(exactly = 1) {
            eventPublisher.publishEvent(any<OrderExecutionRequestEvent>())
        }

        assertThat(eventSlot.captured.tradeId).isEqualTo(99L)
        assertThat(eventSlot.captured.ticker).isEqualTo("AAPL")
        assertThat(eventSlot.captured.quantity).isEqualTo(100) // 10000 / 100
        assertThat(eventSlot.captured.userIdString).isEqualTo("trader-1")
        assertThat(eventSlot.captured.lockedAmount).isEqualByComparingTo(BigDecimal("10000"))
    }
}
